import Foundation
import Metal
import MetalKit
import UIKit
import LeafShared

struct CurlVertex {
    var position: SIMD2<Float>
    var texCoord: SIMD2<Float>
}

struct CurlColorVertex {
    var position: SIMD2<Float>
    var _pad: SIMD2<Float> = .zero
    var color: SIMD4<Float>
}

struct CurlUniforms {
    var viewportSize: SIMD2<Float>
}

/// Metal-based 3D cylindrical page curl renderer for Leaf iOS.
/// Renders both single-page curls and two-page spread curls with identical physics,
/// specular highlights, crease shadows, drop shadows, and reverse paper tint to Android's ReaderScreen.
final class PageCurlRenderer {
    static let shared = PageCurlRenderer()

    let device: MTLDevice?
    private let commandQueue: MTLCommandQueue?
    private let pipelineState: MTLRenderPipelineState?
    private let colorPipelineState: MTLRenderPipelineState?
    private let samplerState: MTLSamplerState?
    private let textureCache = NSCache<AnyObject, MTLTexture>()

    static let meshCols = 36
    static let meshRows = 4

    private init() {
        guard let dev = MTLCreateSystemDefaultDevice() else {
            self.device = nil
            self.commandQueue = nil
            self.pipelineState = nil
            self.colorPipelineState = nil
            self.samplerState = nil
            return
        }
        self.device = dev
        self.commandQueue = dev.makeCommandQueue()

        let shaderSource = """
        #include <metal_stdlib>
        using namespace metal;

        struct VertexIn {
            float2 position;
            float2 texCoord;
        };

        struct VertexOut {
            float4 position [[position]];
            float2 texCoord;
        };

        struct ColorVertexIn {
            float2 position;
            float2 _pad;
            float4 color;
        };

        struct ColorVertexOut {
            float4 position [[position]];
            float4 color;
        };

        struct Uniforms {
            float2 viewportSize;
        };

        vertex VertexOut curl_vertex(
            constant VertexIn *vertices [[buffer(0)]],
            constant Uniforms &uniforms [[buffer(1)]],
            uint vid [[vertex_id]])
        {
            VertexOut out;
            float2 pos = vertices[vid].position;
            out.position = float4(
                (pos.x / (uniforms.viewportSize.x * 0.5)) - 1.0,
                1.0 - (pos.y / (uniforms.viewportSize.y * 0.5)),
                0.0,
                1.0
            );
            out.texCoord = vertices[vid].texCoord;
            return out;
        }

        fragment float4 curl_fragment(
            VertexOut in [[stage_in]],
            texture2d<float> texture [[texture(0)]],
            sampler s [[sampler(0)]])
        {
            return texture.sample(s, in.texCoord);
        }

        vertex ColorVertexOut color_vertex(
            constant ColorVertexIn *vertices [[buffer(0)]],
            constant Uniforms &uniforms [[buffer(1)]],
            uint vid [[vertex_id]])
        {
            ColorVertexOut out;
            float2 pos = vertices[vid].position;
            out.position = float4(
                (pos.x / (uniforms.viewportSize.x * 0.5)) - 1.0,
                1.0 - (pos.y / (uniforms.viewportSize.y * 0.5)),
                0.0,
                1.0
            );
            out.color = vertices[vid].color;
            return out;
        }

        fragment float4 color_fragment(ColorVertexOut in [[stage_in]]) {
            return in.color;
        }
        """

        var pipe: MTLRenderPipelineState? = nil
        var colorPipe: MTLRenderPipelineState? = nil
        var sampler: MTLSamplerState? = nil

        do {
            let library = try dev.makeLibrary(source: shaderSource, options: nil)
            let texVertFunc = library.makeFunction(name: "curl_vertex")
            let texFragFunc = library.makeFunction(name: "curl_fragment")

            let pipeDesc = MTLRenderPipelineDescriptor()
            pipeDesc.vertexFunction = texVertFunc
            pipeDesc.fragmentFunction = texFragFunc
            pipeDesc.colorAttachments[0].pixelFormat = .bgra8Unorm
            pipeDesc.colorAttachments[0].isBlendingEnabled = true
            pipeDesc.colorAttachments[0].rgbBlendOperation = .add
            pipeDesc.colorAttachments[0].alphaBlendOperation = .add
            pipeDesc.colorAttachments[0].sourceRGBBlendFactor = .sourceAlpha
            pipeDesc.colorAttachments[0].sourceAlphaBlendFactor = .sourceAlpha
            pipeDesc.colorAttachments[0].destinationRGBBlendFactor = .oneMinusSourceAlpha
            pipeDesc.colorAttachments[0].destinationAlphaBlendFactor = .oneMinusSourceAlpha
            pipe = try dev.makeRenderPipelineState(descriptor: pipeDesc)

            let colorVertFunc = library.makeFunction(name: "color_vertex")
            let colorFragFunc = library.makeFunction(name: "color_fragment")

            let colorPipeDesc = MTLRenderPipelineDescriptor()
            colorPipeDesc.vertexFunction = colorVertFunc
            colorPipeDesc.fragmentFunction = colorFragFunc
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

    func texture(for image: UIImage) -> MTLTexture? {
        guard let dev = device, let cgImage = image.cgImage else { return nil }
        if let cached = textureCache.object(forKey: cgImage) {
            return cached
        }
        let loader = MTKTextureLoader(device: dev)
        do {
            let tex = try loader.newTexture(cgImage: cgImage, options: [
                .SRGB: false,
                .generateMipmaps: false,
                .origin: MTKTextureLoader.Origin.topLeft
            ])
            textureCache.setObject(tex, forKey: cgImage)
            return tex
        } catch {
            print("Failed to create Metal texture from image: \(error)")
            return nil
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

        var uniforms = CurlUniforms(viewportSize: [Float(viewportSize.width), Float(viewportSize.height)])
        encoder.setRenderPipelineState(pipelineState!)
        encoder.setVertexBytes(meshVerts, length: MemoryLayout<CurlVertex>.stride * meshVerts.count, index: 0)
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
        progress: CGFloat,
        isPeelingFromRight: Bool
    ) {
        guard isAvailable, let queue = commandQueue else { return }
        guard let topTex = texture(for: topImage) else { return }
        let bottomTex = bottomImage.flatMap { texture(for: $0) }

        guard let cmdBuffer = queue.makeCommandBuffer() else { return }
        let passDesc = MTLRenderPassDescriptor()
        passDesc.colorAttachments[0].texture = drawable.texture
        passDesc.colorAttachments[0].loadAction = .clear
        passDesc.colorAttachments[0].clearColor = MTLClearColor(red: 0, green: 0, blue: 0, alpha: 0)
        passDesc.colorAttachments[0].storeAction = .store

        guard let encoder = cmdBuffer.makeRenderCommandEncoder(descriptor: passDesc) else { return }

        let t = Float(min(max(progress, 0), 1))
        let w = Float(draw.scaledWidth)
        let h = Float(draw.scaledHeight)
        let left = Float(draw.left)
        let top = Float(draw.top)

        // 1. Draw underneath page revealed by the curl
        if let bottomTex = bottomTex {
            drawFlatQuad(
                encoder: encoder,
                texture: bottomTex,
                rect: CGRect(x: CGFloat(left), y: CGFloat(top), width: CGFloat(w), height: CGFloat(h)),
                viewportSize: viewportSize
            )
        }

        // 2. Geometry of the vertical cylindrical roll
        let baseR = w * 0.08
        let r = max(baseR * (0.8 + 0.4 * sin(t * Float.pi)), 10.0)

        // Paper tint color: 0xFFFBF0D9 -> RGB: (251/255, 240/255, 217/255)
        let paperR: Float = 251.0 / 255.0
        let paperG: Float = 240.0 / 255.0
        let paperB: Float = 217.0 / 255.0

        if isPeelingFromRight {
            let xFold = left + w * (1.0 - t)
            let xApex = min(xFold + r, left + w)

            // Drop shadow onto underneath page to the right of the apex
            let shadowWidth = max(r * 1.8, 12.0)
            let shadowAlpha = min(max(0.55 * sin(t * Float.pi), 0.0), 0.65)
            if shadowAlpha > 0.02 {
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

            // Deform top page mesh around the vertical cylinder
            var grid = [[CurlVertex]](
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
                        let theta = s / r
                        if theta <= Float.pi {
                            x = xFold + r * sin(theta)
                        } else {
                            x = xFold - (s - Float.pi * r)
                        }
                    }
                    grid[row][col] = CurlVertex(position: [x, yFlat], texCoord: [u, v])
                }
            }
            drawMesh(encoder: encoder, texture: topTex, grid: grid, viewportSize: viewportSize)

            // Crease shadow in the valley at xFold
            let creaseWidth = max(r * 0.75, 8.0)
            let creaseAlpha = min(max(0.4 * sin(t * Float.pi), 0.0), 0.45)
            if creaseAlpha > 0.02 {
                let creaseStart = max(xFold - creaseWidth, left)
                let creaseMid = creaseStart + (xFold - creaseStart) * 0.7
                drawGradientRect(
                    encoder: encoder,
                    xStart: creaseStart, xEnd: creaseMid, y: top, height: h,
                    colorStart: [0, 0, 0, 0],
                    colorEnd: [0, 0, 0, creaseAlpha * 0.5],
                    viewportSize: viewportSize
                )
                drawGradientRect(
                    encoder: encoder,
                    xStart: creaseMid, xEnd: xFold, y: top, height: h,
                    colorStart: [0, 0, 0, creaseAlpha * 0.5],
                    colorEnd: [0, 0, 0, creaseAlpha],
                    viewportSize: viewportSize
                )
            }

            // Specular highlight along apex of the roll
            let highlightWidth = r * 0.6
            let highlightAlpha = min(max(0.22 * sin(t * Float.pi), 0.0), 0.25)
            if highlightAlpha > 0.02 && xApex - highlightWidth > left {
                let hStart = xApex - highlightWidth
                let hEnd = min(xApex + highlightWidth * 0.5, left + w)
                drawGradientRect(
                    encoder: encoder,
                    xStart: hStart, xEnd: xApex, y: top, height: h,
                    colorStart: [1, 1, 1, 0],
                    colorEnd: [1, 1, 1, highlightAlpha],
                    viewportSize: viewportSize
                )
                drawGradientRect(
                    encoder: encoder,
                    xStart: xApex, xEnd: hEnd, y: top, height: h,
                    colorStart: [1, 1, 1, highlightAlpha],
                    colorEnd: [1, 1, 1, 0],
                    viewportSize: viewportSize
                )
            }

            // Reverse side of page tint across the curl
            let reverseAlpha = min(max(0.28 * sin(t * Float.pi), 0.0), 0.35)
            if reverseAlpha > 0.02 && xApex > xFold {
                drawGradientRect(
                    encoder: encoder,
                    xStart: xFold, xEnd: xApex, y: top, height: h,
                    colorStart: [paperR, paperG, paperB, reverseAlpha],
                    colorEnd: [paperR, paperG, paperB, 0],
                    viewportSize: viewportSize
                )
            }
        } else {
            // Peeling from Left edge to Right
            let xFold = left + w * t
            let xApex = max(xFold - r, left)

            // Drop shadow onto underneath page to the left of the apex
            let shadowWidth = max(r * 1.8, 12.0)
            let shadowAlpha = min(max(0.55 * sin(t * Float.pi), 0.0), 0.65)
            if shadowAlpha > 0.02 {
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

            // Deform top page mesh around the vertical cylinder
            var grid = [[CurlVertex]](
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
                        let theta = s / r
                        if theta <= Float.pi {
                            x = xFold - r * sin(theta)
                        } else {
                            x = xFold + (s - Float.pi * r)
                        }
                    }
                    grid[row][col] = CurlVertex(position: [x, yFlat], texCoord: [u, v])
                }
            }
            drawMesh(encoder: encoder, texture: topTex, grid: grid, viewportSize: viewportSize)

            // Crease shadow in the valley at xFold
            let creaseWidth = max(r * 0.75, 8.0)
            let creaseAlpha = min(max(0.4 * sin(t * Float.pi), 0.0), 0.45)
            if creaseAlpha > 0.02 {
                let creaseEnd = min(xFold + creaseWidth, left + w)
                let creaseMid = xFold + (creaseEnd - xFold) * 0.3
                drawGradientRect(
                    encoder: encoder,
                    xStart: xFold, xEnd: creaseMid, y: top, height: h,
                    colorStart: [0, 0, 0, creaseAlpha],
                    colorEnd: [0, 0, 0, creaseAlpha * 0.5],
                    viewportSize: viewportSize
                )
                drawGradientRect(
                    encoder: encoder,
                    xStart: creaseMid, xEnd: creaseEnd, y: top, height: h,
                    colorStart: [0, 0, 0, creaseAlpha * 0.5],
                    colorEnd: [0, 0, 0, 0],
                    viewportSize: viewportSize
                )
            }

            // Specular highlight along apex of the roll
            let highlightWidth = r * 0.6
            let highlightAlpha = min(max(0.22 * sin(t * Float.pi), 0.0), 0.25)
            if highlightAlpha > 0.02 && xApex + highlightWidth < left + w {
                let hStart = max(xApex - highlightWidth * 0.5, left)
                let hEnd = xApex + highlightWidth
                drawGradientRect(
                    encoder: encoder,
                    xStart: hStart, xEnd: xApex, y: top, height: h,
                    colorStart: [1, 1, 1, 0],
                    colorEnd: [1, 1, 1, highlightAlpha],
                    viewportSize: viewportSize
                )
                drawGradientRect(
                    encoder: encoder,
                    xStart: xApex, xEnd: hEnd, y: top, height: h,
                    colorStart: [1, 1, 1, highlightAlpha],
                    colorEnd: [1, 1, 1, 0],
                    viewportSize: viewportSize
                )
            }

            // Reverse side of page tint across the curl
            let reverseAlpha = min(max(0.28 * sin(t * Float.pi), 0.0), 0.35)
            if reverseAlpha > 0.02 && xFold > xApex {
                drawGradientRect(
                    encoder: encoder,
                    xStart: xApex, xEnd: xFold, y: top, height: h,
                    colorStart: [paperR, paperG, paperB, 0],
                    colorEnd: [paperR, paperG, paperB, reverseAlpha],
                    viewportSize: viewportSize
                )
            }
        }

        encoder.endEncoding()
        cmdBuffer.present(drawable)
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
        userPanY: CGFloat
    ) {
        guard isAvailable, let queue = commandQueue else { return }
        guard let staticTex = texture(for: staticImage),
              let movingFrontTex = texture(for: movingFrontImage) else { return }
        let movingBackTex = movingBackImage.flatMap { texture(for: $0) }
        let underneathTex = underneathImage.flatMap { texture(for: $0) }

        guard let cmdBuffer = queue.makeCommandBuffer() else { return }
        let passDesc = MTLRenderPassDescriptor()
        passDesc.colorAttachments[0].texture = drawable.texture
        passDesc.colorAttachments[0].loadAction = .clear
        passDesc.colorAttachments[0].clearColor = MTLClearColor(red: 0, green: 0, blue: 0, alpha: 0)
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
        cmdBuffer.present(drawable)
        cmdBuffer.commit()
    }
}
