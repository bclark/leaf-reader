import Foundation
import Metal
import MetalKit
import UIKit
import LeafShared

/// Vertex structure passed to the Metal page curl shaders.
struct CurlVertex {
    var position: SIMD2<Float>
    var texCoord: SIMD2<Float>
}

/// Vertex structure for untextured gradient / shadow / highlight quads.
struct CurlColorVertex {
    var position: SIMD2<Float>
    var color: SIMD4<Float>
}

/// Uniforms for converting screen-space coordinates to Normalized Device Coordinates (NDC).
struct CurlUniforms {
    var viewportSize: SIMD2<Float>
}

/// Singleton Metal renderer that compiles the page-curl shaders once and renders
/// both single-page 3D cylindrical curls and open-book two-page spread curls.
final class PageCurlRenderer {
    static let shared = PageCurlRenderer()

    static let meshCols = 36
    static let meshRows = 4

    let device: MTLDevice?
    let commandQueue: MTLCommandQueue?
    private let pipelineState: MTLRenderPipelineState?
    private let colorPipelineState: MTLRenderPipelineState?
    private let samplerState: MTLSamplerState?
    private let textureCache = NSCache<AnyObject, MTLTexture>()

    private static let shaderSource = """
    #include <metal_stdlib>
    using namespace metal;

    struct CurlVertexIn {
        float2 position [[attribute(0)]];
        float2 texCoord [[attribute(1)]];
    };

    struct CurlVertexOut {
        float4 position [[position]];
        float2 texCoord;
    };

    struct CurlColorVertexIn {
        float2 position [[attribute(0)]];
        float4 color [[attribute(1)]];
    };

    struct CurlColorVertexOut {
        float4 position [[position]];
        float4 color;
    };

    struct CurlUniforms {
        float2 viewportSize;
    };

    vertex CurlVertexOut curl_vertex(
        uint vid [[vertex_id]],
        constant float4 *vertices [[buffer(0)]],
        constant CurlUniforms &uniforms [[buffer(1)]]
    ) {
        float4 v = vertices[vid];
        float2 pos = v.xy;
        float2 uv = v.zw;

        CurlVertexOut out;
        out.position = float4(
            (pos.x / (uniforms.viewportSize.x * 0.5)) - 1.0,
            1.0 - (pos.y / (uniforms.viewportSize.y * 0.5)),
            0.0,
            1.0
        );
        out.texCoord = uv;
        return out;
    }

    fragment float4 curl_fragment(
        CurlVertexOut in [[stage_in]],
        texture2d<float> tex [[texture(0)]],
        sampler s [[sampler(0)]]
    ) {
        return tex.sample(s, in.texCoord);
    }

    struct ColorVertRaw {
        float2 position;
        float2 _pad;
        float4 color;
    };

    vertex CurlColorVertexOut color_vertex(
        uint vid [[vertex_id]],
        constant ColorVertRaw *vertices [[buffer(0)]],
        constant CurlUniforms &uniforms [[buffer(1)]]
    ) {
        ColorVertRaw v = vertices[vid];
        float2 pos = v.position;

        CurlColorVertexOut out;
        out.position = float4(
            (pos.x / (uniforms.viewportSize.x * 0.5)) - 1.0,
            1.0 - (pos.y / (uniforms.viewportSize.y * 0.5)),
            0.0,
            1.0
        );
        out.color = v.color;
        return out;
    }

    fragment float4 color_fragment(
        CurlColorVertexOut in [[stage_in]]
    ) {
        return in.color;
    }
    """

    private init() {
        textureCache.countLimit = 12

        guard let dev = MTLCreateSystemDefaultDevice(),
              let queue = dev.makeCommandQueue() else {
            self.device = nil
            self.commandQueue = nil
            self.pipelineState = nil
            self.colorPipelineState = nil
            self.samplerState = nil
            return
        }
        self.device = dev
        self.commandQueue = queue

        var pipe: MTLRenderPipelineState?
        var colorPipe: MTLRenderPipelineState?
        var sampler: MTLSamplerState?

        do {
            let library = try dev.makeLibrary(source: Self.shaderSource, options: nil)

            let pipeDesc = MTLRenderPipelineDescriptor()
            pipeDesc.vertexFunction = library.makeFunction(name: "curl_vertex")
            pipeDesc.fragmentFunction = library.makeFunction(name: "curl_fragment")
            pipeDesc.colorAttachments[0].pixelFormat = .bgra8Unorm
            pipeDesc.colorAttachments[0].isBlendingEnabled = true
            pipeDesc.colorAttachments[0].rgbBlendOperation = .add
            pipeDesc.colorAttachments[0].alphaBlendOperation = .add
            pipeDesc.colorAttachments[0].sourceRGBBlendFactor = .sourceAlpha
            pipeDesc.colorAttachments[0].sourceAlphaBlendFactor = .sourceAlpha
            pipeDesc.colorAttachments[0].destinationRGBBlendFactor = .oneMinusSourceAlpha
            pipeDesc.colorAttachments[0].destinationAlphaBlendFactor = .oneMinusSourceAlpha
            pipe = try dev.makeRenderPipelineState(descriptor: pipeDesc)

            let colorPipeDesc = MTLRenderPipelineDescriptor()
            colorPipeDesc.vertexFunction = library.makeFunction(name: "color_vertex")
            colorPipeDesc.fragmentFunction = library.makeFunction(name: "color_fragment")
            colorPipeDesc.colorAttachments[0].pixelFormat = .bgra8Unorm
            colorPipeDesc.colorAttachments[0].isBlendingEnabled = true
            colorPipeDesc.colorAttachments[0].rgbBlendOperation = .add
            colorPipeDesc.colorAttachments[0].alphaBlendOperation = .add
            colorPipeDesc.colorAttachments[0].sourceRGBBlendFactor = .sourceAlpha
            colorPipeDesc.colorAttachments[0].sourceAlphaBlendFactor = .sourceAlpha
            colorPipeDesc.colorAttachments[0].destinationRGBBlendFactor = .oneMinusSourceAlpha
            colorPipeDesc.colorAttachments[0].destinationAlphaBlendFactor = .oneMinusSourceAlpha
            colorPipe = try dev.makeRenderPipelineState(descriptor: colorPipeDesc)

            let samplerDesc = MTLSamplerDescriptor()
            samplerDesc.minFilter = .linear
            samplerDesc.magFilter = .linear
            samplerDesc.sAddressMode = .clampToEdge
            samplerDesc.tAddressMode = .clampToEdge
            sampler = dev.makeSamplerState(descriptor: samplerDesc)
        } catch {
            print("Failed to compile Metal shaders: \(error)")
        }

        self.pipelineState = pipe
        self.colorPipelineState = colorPipe
        self.samplerState = sampler
    }

    var isAvailable: Bool {
        device != nil && pipelineState != nil && colorPipelineState != nil && samplerState != nil
    }

    /// Converts a UIImage into a BGRA8Unorm MTLTexture, handling grayscale, RGB, RGBA, and wide-gamut images reliably.
    func texture(for image: UIImage) -> MTLTexture? {
        guard let dev = device, let cgImage = image.cgImage else { return nil }
        if let cached = textureCache.object(forKey: cgImage) {
            return cached
        }

        let width = cgImage.width
        let height = cgImage.height
        guard width > 0, height > 0 else { return nil }

        let bytesPerPixel = 4
        let bytesPerRow = width * bytesPerPixel
        let colorSpace = CGColorSpaceCreateDeviceRGB()
        let bitmapInfo = CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue

        guard let context = CGContext(
            data: nil,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: bytesPerRow,
            space: colorSpace,
            bitmapInfo: bitmapInfo
        ), let data = context.data else {
            return nil
        }

        context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))

        let desc = MTLTextureDescriptor.texture2DDescriptor(
            pixelFormat: .bgra8Unorm,
            width: width,
            height: height,
            mipmapped: false
        )
        desc.usage = [.shaderRead]
        desc.storageMode = .shared

        guard let tex = dev.makeTexture(descriptor: desc) else { return nil }
        tex.replace(
            region: MTLRegionMake2D(0, 0, width, height),
            mipmapLevel: 0,
            withBytes: data,
            bytesPerRow: bytesPerRow
        )
        textureCache.setObject(tex, forKey: cgImage)
        return tex
    }

    func prewarmTextures(for images: [UIImage?]) {
        for img in images {
            if let img = img {
                _ = texture(for: img)
            }
        }
    }

    // MARK: - Drawing Helpers

    private func drawFlatQuad(
        encoder: MTLRenderCommandEncoder,
        texture: MTLTexture,
        rect: CGRect,
        viewportSize: CGSize
    ) {
        let x1 = Float(rect.minX)
        let y1 = Float(rect.minY)
        let x2 = Float(rect.maxX)
        let y2 = Float(rect.maxY)

        let v0 = CurlVertex(position: [x1, y1], texCoord: [0, 0])
        let v1 = CurlVertex(position: [x1, y2], texCoord: [0, 1])
        let v2 = CurlVertex(position: [x2, y1], texCoord: [1, 0])
        let v3 = CurlVertex(position: [x2, y2], texCoord: [1, 1])

        let verts: [CurlVertex] = [v0, v1, v2, v2, v1, v3]
        var uniforms = CurlUniforms(viewportSize: [Float(viewportSize.width), Float(viewportSize.height)])

        encoder.setRenderPipelineState(pipelineState!)
        encoder.setVertexBytes(verts, length: MemoryLayout<CurlVertex>.stride * verts.count, index: 0)
        encoder.setVertexBytes(&uniforms, length: MemoryLayout<CurlUniforms>.stride, index: 1)
        encoder.setFragmentTexture(texture, index: 0)
        encoder.setFragmentSamplerState(samplerState, index: 0)
        encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 6)
    }

    private func drawGradientRect(
        encoder: MTLRenderCommandEncoder,
        xStart: Float,
        xEnd: Float,
        y: Float,
        height: Float,
        colorStart: SIMD4<Float>,
        colorEnd: SIMD4<Float>,
        viewportSize: CGSize
    ) {
        guard xEnd > xStart else { return }
        let v0 = CurlColorVertex(position: [xStart, y], color: colorStart)
        let v1 = CurlColorVertex(position: [xStart, y + height], color: colorStart)
        let v2 = CurlColorVertex(position: [xEnd, y], color: colorEnd)
        let v3 = CurlColorVertex(position: [xEnd, y + height], color: colorEnd)

        let verts: [CurlColorVertex] = [v0, v1, v2, v2, v1, v3]
        var uniforms = CurlUniforms(viewportSize: [Float(viewportSize.width), Float(viewportSize.height)])

        encoder.setRenderPipelineState(colorPipelineState!)
        encoder.setVertexBytes(verts, length: MemoryLayout<CurlColorVertex>.stride * verts.count, index: 0)
        encoder.setVertexBytes(&uniforms, length: MemoryLayout<CurlUniforms>.stride, index: 1)
        encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: 6)
    }

    private func drawMesh(
        encoder: MTLRenderCommandEncoder,
        texture: MTLTexture,
        grid: [[CurlVertex]],
        viewportSize: CGSize
    ) {
        guard let dev = device else { return }
        var meshVerts = [CurlVertex]()
        meshVerts.reserveCapacity(Self.meshCols * Self.meshRows * 6)

        for row in 0..<Self.meshRows {
            for col in 0..<Self.meshCols {
                let tl = grid[row][col]
                let tr = grid[row][col + 1]
                let bl = grid[row + 1][col]
                let br = grid[row + 1][col + 1]

                meshVerts.append(tl)
                meshVerts.append(bl)
                meshVerts.append(tr)

                meshVerts.append(tr)
                meshVerts.append(bl)
                meshVerts.append(br)
            }
        }

        let byteLength = MemoryLayout<CurlVertex>.stride * meshVerts.count
        guard let vertexBuffer = dev.makeBuffer(bytes: meshVerts, length: byteLength, options: .storageModeShared) else {
            return
        }

        var uniforms = CurlUniforms(viewportSize: [Float(viewportSize.width), Float(viewportSize.height)])
        encoder.setRenderPipelineState(pipelineState!)
        encoder.setVertexBuffer(vertexBuffer, offset: 0, index: 0)
        encoder.setVertexBytes(&uniforms, length: MemoryLayout<CurlUniforms>.stride, index: 1)
        encoder.setFragmentTexture(texture, index: 0)
        encoder.setFragmentSamplerState(samplerState, index: 0)
        encoder.drawPrimitives(type: .triangle, vertexStart: 0, vertexCount: meshVerts.count)
    }

    // MARK: - Single Page 3D Cylindrical Curl

    func renderSinglePageCurl(
        to drawable: CAMetalDrawable,
        viewportSize: CGSize,
        topImage: UIImage,
        bottomImage: UIImage?,
        draw: PageDraw,
        bottomDraw: PageDraw? = nil,
        progress: CGFloat,
        isPeelingFromRight: Bool,
        clearColor: MTLClearColor = MTLClearColor(red: 20.0 / 255.0, green: 25.0 / 255.0, blue: 28.0 / 255.0, alpha: 1.0)
    ) {
        renderSinglePageCurl(
            targetTexture: drawable.texture,
            drawableToPresent: drawable,
            viewportSize: viewportSize,
            topImage: topImage,
            bottomImage: bottomImage,
            draw: draw,
            bottomDraw: bottomDraw,
            progress: progress,
            isPeelingFromRight: isPeelingFromRight,
            clearColor: clearColor
        )
    }

    func renderSinglePageCurl(
        targetTexture: MTLTexture,
        drawableToPresent: CAMetalDrawable? = nil,
        viewportSize: CGSize,
        topImage: UIImage,
        bottomImage: UIImage?,
        draw: PageDraw,
        bottomDraw: PageDraw? = nil,
        progress: CGFloat,
        isPeelingFromRight: Bool,
        clearColor: MTLClearColor = MTLClearColor(red: 20.0 / 255.0, green: 25.0 / 255.0, blue: 28.0 / 255.0, alpha: 1.0)
    ) {
        guard isAvailable, let queue = commandQueue else { return }
        guard let topTex = texture(for: topImage) else { return }
        let bottomTex = bottomImage.flatMap { texture(for: $0) }

        guard let cmdBuffer = queue.makeCommandBuffer() else { return }
        let passDesc = MTLRenderPassDescriptor()
        passDesc.colorAttachments[0].texture = targetTexture
        passDesc.colorAttachments[0].loadAction = .clear
        passDesc.colorAttachments[0].clearColor = clearColor
        passDesc.colorAttachments[0].storeAction = .store

        guard let encoder = cmdBuffer.makeRenderCommandEncoder(descriptor: passDesc) else { return }

        let t = Float(min(max(progress, 0), 1))
        let w = Float(draw.scaledWidth)
        let h = Float(draw.scaledHeight)
        let left = Float(draw.left)
        let top = Float(draw.top)

        // 1. Draw underneath page revealed by the curl
        if let bottomTex = bottomTex {
            let bDraw = bottomDraw ?? draw
            drawFlatQuad(
                encoder: encoder,
                texture: bottomTex,
                rect: CGRect(
                    x: CGFloat(bDraw.left),
                    y: CGFloat(bDraw.top),
                    width: CGFloat(bDraw.scaledWidth),
                    height: CGFloat(bDraw.scaledHeight)
                ),
                viewportSize: viewportSize
            )
        }

        // A broad radius lets the sheet bend gradually instead of forming a tight roll.
        let r = max(w * 0.30, 1.0)

        if isPeelingFromRight {
            // Carry the broad bend past the page edge so it clears fully at completion.
            let xFold = left + w - (w + r) * t
            let sMax = max((left + w) - xFold, 0.0)
            let thetaMax = sMax / r
            let xApex = min(max(xFold + r * sin(min(thetaMax, Float.pi / 2.0)), left), left + w)

            // Drop shadow onto underneath page to the right of the apex
            let shadowWidth = max(w * 0.06, 4.0)
            let shadowAlpha = max(0.20 * sin(t * Float.pi), 0.0)
            if shadowAlpha > 0.02 && xApex < left + w {
                let shadowEnd = min(xApex + shadowWidth, left + w)
                let shadowMid = xApex + (shadowEnd - xApex) * 0.4
                drawGradientRect(
                    encoder: encoder,
                    xStart: xApex, xEnd: shadowMid, y: top, height: h,
                    colorStart: [0, 0, 0, shadowAlpha],
                    colorEnd: [0, 0, 0, shadowAlpha * 0.45],
                    viewportSize: viewportSize
                )
                drawGradientRect(
                    encoder: encoder,
                    xStart: shadowMid, xEnd: shadowEnd, y: top, height: h,
                    colorStart: [0, 0, 0, shadowAlpha * 0.45],
                    colorEnd: [0, 0, 0, 0],
                    viewportSize: viewportSize
                )
            }

            // Deform top page front face around the vertical cylinder (clamped at cylinder horizon pi/2)
            var frontGrid = [[CurlVertex]](
                repeating: [CurlVertex](repeating: CurlVertex(position: .zero, texCoord: .zero), count: Self.meshCols + 1),
                count: Self.meshRows + 1
            )
            for row in 0...Self.meshRows {
                let v = Float(row) / Float(Self.meshRows)
                let yFlat = top + v * h
                for col in 0...Self.meshCols {
                    let u = Float(col) / Float(Self.meshCols)
                    let xFlat = left + u * w
                    let x: Float
                    if xFlat <= xFold {
                        x = xFlat
                    } else {
                        let s = xFlat - xFold
                        let theta = min(s / r, Float.pi / 2.0)
                        x = min(max(xFold + r * sin(theta), left), left + w)
                    }
                    frontGrid[row][col] = CurlVertex(position: [x, yFlat], texCoord: [u, v])
                }
            }
            drawMesh(encoder: encoder, texture: topTex, grid: frontGrid, viewportSize: viewportSize)

            // Back of the rolled sheet (coiled between xFold and xApex when thetaMax > pi/2)
            if thetaMax > Float.pi / 2.0 {
                var backGrid = [[CurlVertex]](
                    repeating: [CurlVertex](repeating: CurlVertex(position: .zero, texCoord: .zero), count: Self.meshCols + 1),
                    count: Self.meshRows + 1
                )
                for row in 0...Self.meshRows {
                    let v = Float(row) / Float(Self.meshRows)
                    let yFlat = top + v * h
                    for col in 0...Self.meshCols {
                        let u = Float(col) / Float(Self.meshCols)
                        let xFlat = left + u * w
                        let s = max(xFlat - xFold, (Float.pi / 2.0) * r)
                        let theta = min(s / r, Float.pi)
                        let x = min(max(xFold + r * sin(theta), left), left + w)
                        backGrid[row][col] = CurlVertex(position: [x, yFlat], texCoord: [u, v])
                    }
                }
                drawMesh(encoder: encoder, texture: topTex, grid: backGrid, viewportSize: viewportSize)
            }

        } else {
            // Peeling from Left edge to Right
            let xFold = left + (w + r) * t
            let sMax = max(xFold - left, 0.0)
            let thetaMax = sMax / r
            let xApex = min(max(xFold - r * sin(min(thetaMax, Float.pi / 2.0)), left), left + w)

            // Drop shadow onto underneath page to the left of the apex
            let shadowWidth = max(w * 0.06, 4.0)
            let shadowAlpha = max(0.20 * sin(t * Float.pi), 0.0)
            if shadowAlpha > 0.02 && xApex > left {
                let shadowStart = max(xApex - shadowWidth, left)
                let shadowMid = shadowStart + (xApex - shadowStart) * 0.6
                drawGradientRect(
                    encoder: encoder,
                    xStart: shadowStart, xEnd: shadowMid, y: top, height: h,
                    colorStart: [0, 0, 0, 0],
                    colorEnd: [0, 0, 0, shadowAlpha * 0.45],
                    viewportSize: viewportSize
                )
                drawGradientRect(
                    encoder: encoder,
                    xStart: shadowMid, xEnd: xApex, y: top, height: h,
                    colorStart: [0, 0, 0, shadowAlpha * 0.45],
                    colorEnd: [0, 0, 0, shadowAlpha],
                    viewportSize: viewportSize
                )
            }

            // Deform top page front face around the vertical cylinder (clamped at cylinder horizon pi/2)
            var frontGrid = [[CurlVertex]](
                repeating: [CurlVertex](repeating: CurlVertex(position: .zero, texCoord: .zero), count: Self.meshCols + 1),
                count: Self.meshRows + 1
            )
            for row in 0...Self.meshRows {
                let v = Float(row) / Float(Self.meshRows)
                let yFlat = top + v * h
                for col in 0...Self.meshCols {
                    let u = Float(col) / Float(Self.meshCols)
                    let xFlat = left + u * w
                    let x: Float
                    if xFlat >= xFold {
                        x = xFlat
                    } else {
                        let s = xFold - xFlat
                        let theta = min(s / r, Float.pi / 2.0)
                        x = min(max(xFold - r * sin(theta), left), left + w)
                    }
                    frontGrid[row][col] = CurlVertex(position: [x, yFlat], texCoord: [u, v])
                }
            }
            drawMesh(encoder: encoder, texture: topTex, grid: frontGrid, viewportSize: viewportSize)

            // Back of the rolled sheet (coiled between xApex and xFold when thetaMax > pi/2)
            if thetaMax > Float.pi / 2.0 {
                var backGrid = [[CurlVertex]](
                    repeating: [CurlVertex](repeating: CurlVertex(position: .zero, texCoord: .zero), count: Self.meshCols + 1),
                    count: Self.meshRows + 1
                )
                for row in 0...Self.meshRows {
                    let v = Float(row) / Float(Self.meshRows)
                    let yFlat = top + v * h
                    for col in 0...Self.meshCols {
                        let u = Float(col) / Float(Self.meshCols)
                        let xFlat = left + u * w
                        let s = max(xFold - xFlat, (Float.pi / 2.0) * r)
                        let theta = min(s / r, Float.pi)
                        let x = min(max(xFold - r * sin(theta), left), left + w)
                        backGrid[row][col] = CurlVertex(position: [x, yFlat], texCoord: [u, v])
                    }
                }
                drawMesh(encoder: encoder, texture: topTex, grid: backGrid, viewportSize: viewportSize)
            }

        }

        encoder.endEncoding()
        if let drawable = drawableToPresent {
            cmdBuffer.present(drawable)
        }
        cmdBuffer.commit()
    }

    // MARK: - Two-Page Spread 3D Curl

    func renderSpreadCurl(
        to drawable: CAMetalDrawable,
        viewportSize: CGSize,
        spread: SpreadDraw,
        destinationSpread: SpreadDraw,
        staticImage: UIImage,
        movingFrontImage: UIImage,
        movingBackImage: UIImage?,
        underneathImage: UIImage?,
        mapping: SpreadTurnMapping,
        progress: CGFloat,
        userScale: CGFloat,
        userPanX: CGFloat,
        userPanY: CGFloat,
        clearColor: MTLClearColor = MTLClearColor(red: 20.0 / 255.0, green: 25.0 / 255.0, blue: 28.0 / 255.0, alpha: 1.0)
    ) {
        renderSpreadCurl(
            targetTexture: drawable.texture,
            drawableToPresent: drawable,
            viewportSize: viewportSize,
            spread: spread,
            destinationSpread: destinationSpread,
            staticImage: staticImage,
            movingFrontImage: movingFrontImage,
            movingBackImage: movingBackImage,
            underneathImage: underneathImage,
            mapping: mapping,
            progress: progress,
            userScale: userScale,
            userPanX: userPanX,
            userPanY: userPanY,
            clearColor: clearColor
        )
    }

    func renderSpreadCurl(
        targetTexture: MTLTexture,
        drawableToPresent: CAMetalDrawable? = nil,
        viewportSize: CGSize,
        spread: SpreadDraw,
        destinationSpread: SpreadDraw,
        staticImage: UIImage,
        movingFrontImage: UIImage,
        movingBackImage: UIImage?,
        underneathImage: UIImage?,
        mapping: SpreadTurnMapping,
        progress: CGFloat,
        userScale: CGFloat,
        userPanX: CGFloat,
        userPanY: CGFloat,
        clearColor: MTLClearColor = MTLClearColor(red: 20.0 / 255.0, green: 25.0 / 255.0, blue: 28.0 / 255.0, alpha: 1.0)
    ) {
        guard isAvailable, let queue = commandQueue else { return }
        guard let staticTex = texture(for: staticImage),
              let movingFrontTex = texture(for: movingFrontImage) else { return }
        let movingBackTex = movingBackImage.flatMap { texture(for: $0) }
        let underneathTex = underneathImage.flatMap { texture(for: $0) }

        guard let cmdBuffer = queue.makeCommandBuffer() else { return }
        let passDesc = MTLRenderPassDescriptor()
        passDesc.colorAttachments[0].texture = targetTexture
        passDesc.colorAttachments[0].loadAction = .clear
        passDesc.colorAttachments[0].clearColor = clearColor
        passDesc.colorAttachments[0].storeAction = .store

        guard let encoder = cmdBuffer.makeRenderCommandEncoder(descriptor: passDesc) else { return }

        let t = Float(min(max(progress, 0), 1))
        let frame = CameraTransformKt.lerpSpreadDraw(from: spread, to: destinationSpread, progress: t)
        let cx = Float(viewportSize.width) / 2.0
        let cy = Float(viewportSize.height) / 2.0
        let scale = Float(userScale)
        let panX = Float(userPanX)
        let panY = Float(userPanY)

        let left = cx + (frame.left1 - cx) * scale + panX
        let spine = cx + (frame.left2 - cx) * scale + panX
        let top = cy + (frame.top1 - cy) * scale + panY
        let leftWidth = frame.width1 * scale
        let rightWidth = frame.width2 * scale
        let height = frame.height1 * scale
        let fromRight = mapping.isPeelingFromRight
        let width = CameraTransformKt.spreadTurnSheetWidth(from: spread, to: destinationSpread, progress: t, fromRight: fromRight) * scale
        let radius = max(width * 0.16 * sin(t * Float.pi), 0.0)
        let fold = CameraTransformKt.computeSpreadCurlFold(width: width, progress: t, radius: radius)
        let faceBoundary = min(max(fold + radius * Float.pi / 2.0, 0.0), width)

        // Draw static page
        let staticRect = CGRect(
            x: CGFloat(fromRight ? left : spine),
            y: CGFloat(top),
            width: CGFloat(fromRight ? leftWidth : rightWidth),
            height: CGFloat(height)
        )
        drawFlatQuad(encoder: encoder, texture: staticTex, rect: staticRect, viewportSize: viewportSize)

        // Draw underneath page
        if let underneathTex = underneathTex {
            let underneathRect = CGRect(
                x: CGFloat(fromRight ? spine : left),
                y: CGFloat(top),
                width: CGFloat(fromRight ? rightWidth : leftWidth),
                height: CGFloat(height)
            )
            drawFlatQuad(encoder: encoder, texture: underneathTex, rect: underneathRect, viewportSize: viewportSize)
        }

        // Draw moving front and back faces with mesh
        func buildFaceGrid(back: Bool) -> [[CurlVertex]] {
            var grid = [[CurlVertex]](
                repeating: [CurlVertex](repeating: CurlVertex(position: .zero, texCoord: .zero), count: Self.meshCols + 1),
                count: Self.meshRows + 1
            )
            for row in 0...Self.meshRows {
                let v = Float(row) / Float(Self.meshRows)
                let y = top + v * height
                for col in 0...Self.meshCols {
                    let u = Float(col) / Float(Self.meshCols)
                    let x = CameraTransformKt.computeSpreadCurlFaceX(
                        u: u,
                        width: width,
                        fold: fold,
                        radius: radius,
                        spine: spine,
                        fromRight: fromRight,
                        back: back
                    )
                    grid[row][col] = CurlVertex(position: [x, y], texCoord: [u, v])
                }
            }
            return grid
        }

        let frontGrid = buildFaceGrid(back: false)
        drawMesh(encoder: encoder, texture: movingFrontTex, grid: frontGrid, viewportSize: viewportSize)

        if let movingBackTex = movingBackTex {
            let backGrid = buildFaceGrid(back: true)
            drawMesh(encoder: encoder, texture: movingBackTex, grid: backGrid, viewportSize: viewportSize)
        }

        // Specular highlight along roll apex
        if radius > 1.0 {
            let apex = CameraTransformKt.computeSpreadCurlX(
                s: faceBoundary,
                sFold: fold,
                r: radius,
                spineX: spine,
                isPeelingFromRight: fromRight
            )
            let highlight = radius * 0.35
            drawGradientRect(
                encoder: encoder,
                xStart: apex - highlight, xEnd: apex, y: top, height: height,
                colorStart: [1, 1, 1, 0],
                colorEnd: [1, 1, 1, 0.15],
                viewportSize: viewportSize
            )
            drawGradientRect(
                encoder: encoder,
                xStart: apex, xEnd: apex + highlight, y: top, height: height,
                colorStart: [1, 1, 1, 0.15],
                colorEnd: [1, 1, 1, 0],
                viewportSize: viewportSize
            )
        }

        encoder.endEncoding()
        if let drawable = drawableToPresent {
            cmdBuffer.present(drawable)
        }
        cmdBuffer.commit()
    }
}
