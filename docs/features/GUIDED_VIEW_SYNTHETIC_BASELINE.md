# Guided View synthetic parity baseline

These synthetic cases characterize existing behavior. No model or comic artwork is needed. Use normalized page edges in `[left, top, right, bottom]` order.

| Case | Panels | Shuffled input | Expected order |
| --- | --- | --- | --- |
| Grid | A=[.05,.05,.45,.40], B=[.55,.05,.95,.40], C=[.05,.60,.45,.95], D=[.55,.60,.95,.95] | D, B, C, A | LTR: A, B, C, D; RTL: B, A, D, C |
| Inset | G=[.05,.05,.90,.65], I=[.60,.26,.79,.40], F=[.08,.75,.91,.96] | F, I, G | LTR: G, I, F |

The inset is ordering input, not a claim that current model decoding retains all nested boxes.

- The raw reliability gate rejects empty input, a single inset, and a full-page box plus an inset. It accepts the non-overlapping grid.
- A synthetic decoder row `[.75,.15,.30,.50,.95,0]` has reversed x edges and yields no detected panel.
- The existing legacy zoom pipeline can synthesize geometry from no detections. That is not detection evidence or proof of the reader’s whole-page fallback.

The companion iOS bridge test should use these same grid and inset vectors. Reader interactions, camera framing, device acceptance, and live detector quality are outside this baseline.
