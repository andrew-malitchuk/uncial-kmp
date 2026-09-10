# Coordinates

Uncial has **one** coordinate model, everywhere, on every platform and from both extraction
paths:

> **Top-down pixels. The origin `(0, 0)` is the top-left corner of the page, `x` grows right,
> `y` grows down.**

Nothing in the public API reports anything else. You never have to ask which engine produced a
box.

---

## Why this is worth stating

The sources disagree, and they disagree in different ways:

| Source | Native convention |
|---|---|
| Tesseract | top-down pixels — already what Uncial uses |
| Apple Vision | **normalized** `0..1`, origin at the **bottom-left** |
| PDF text layer | points (72 per inch), origin at the **bottom-left** of the crop box |

Each engine converts at its own edge. The Vision recognizer has exactly one place where
`(1 - (y + height)) × pageHeight` happens; the `pdf-text` module lifts boxes off the baseline
and scales points to pixels. A caller sees the result, never the conversion.

---

## `BoundingBox`

Every box in the model — `OcrLine.box`, `OcrWord.box` — is an axis-aligned
`BoundingBox` in `io.github.andrewmalitchuk.uncial.model.source.geometry`:

```kotlin
public data class BoundingBox(
    public val left: Float,
    public val top: Float,
    public val width: Float,
    public val height: Float,
)
```

`top` is the distance from the page's **top** edge. Derived members:

| Member | Meaning |
|---|---|
| `right` | `left + width` |
| `bottom` | `top + height` — **larger** than `top`, because `y` grows downwards |
| `center` | A `Point` at the geometric centre; useful for reading-order and column heuristics |
| `area` | `width * height` |
| `union(other)` | The smallest box containing both |
| `BoundingBox.Zero` | A degenerate box at the origin, used where an engine reports no geometry |

`Point(x, y)` and `Size(width, height)` use the same units; `Size.isEmpty` is true when either
dimension is zero or negative.

---

## The page is the frame of reference

Boxes are in page pixels, and `OcrPage.size` is that page's size **in the same units**:

```kotlin
val page = document.pages[0]
val line = page.lines.first()

// Normalized 0..1, if that is what your UI wants.
val x = line.box.left / page.size.width
val y = line.box.top / page.size.height
```

`OcrPage.size` is the **rasterized** size, which depends on the dpi the page was rendered at.
It is not the PDF's point size, and it is not the same across documents. Always normalize
against the page you got the box from.

Drawing an overlay on top of a rendered page is therefore a plain scale from page pixels to
view pixels:

```kotlin
fun BoundingBox.toView(page: OcrPage, viewWidth: Float, viewHeight: Float): BoundingBox {
    val sx = viewWidth / page.size.width
    val sy = viewHeight / page.size.height
    return BoundingBox(left * sx, top * sy, width * sx, height * sy)
}
```

---

## Digital and OCR'd pages are directly comparable

`pdf-text` could have handed back a second coordinate system — PDF points — and left callers to
reconcile the two. It does not. It scales every coordinate by `OcrOptions.renderScale`
(`renderDpi / 72`), so a page read from the text layer lands in the same pixel space as a page
that went through an engine.

This matters most in a `Mixed` document, where a scanner OCR'd the cover page and the rest is
image-only. Both kinds of page carry comparable geometry, which is what lets
`DocumentStructure` take one median type size across the whole document — `OcrLine.fontSize` is
scaled by `renderScale` too, for exactly that reason.

It also means the number in `fontSize` is in page pixels, not points: at the default 200 dpi a
12 pt heading reads as roughly 33. Compare it against other lines of the same document, never
against an absolute point size.

!!! note "Two frames that are not the same thing"
    `renderScale` is derived from `renderDpi` alone. The **rasterizer** additionally clamps the
    dpi when a page would exceed `maxPageSide` (see
    [Options](options.md#renderdpi-vs-maxpageside-the-effective-dpi)). For ordinary page sizes
    at the default settings no clamping happens and the two frames coincide. On a page large
    enough to be clamped, an OCR'd page is rendered smaller than the digital layer's scaled
    points would suggest — which is another reason to normalize against `OcrPage.size` rather
    than assuming a scale.

---

## Rotation and skew

Two separate things, at two different granularities.

**`OcrPage.orientation`** is coarse page rotation in 90° steps — `Orientation.Up`, `Right`,
`Down`, `Left`, with `.degrees` giving `0`, `90`, `180`, `270`. `Up` is the default and is what
you get from engines that do not measure it; only the JVM Tesseract engine currently detects
orientation, which `OcrCapabilities.orientationDetection` reports.

**`OcrLine.skewDegrees`** is the fine rotation of a crooked scan, positive clockwise, `0f` for a
straight line and for engines that do not measure it (`OcrCapabilities.skewDetection`). Note
that `box` stays axis-aligned regardless — a skewed line's box is its bounding rectangle, not a
rotated quad.

For PDF pages, Uncial renders the **crop box**, not the media box, and swaps the reported page
size for `/Rotate 90` and `/Rotate 270` — so `OcrPage.size` and the boxes on it are always in
the same frame as each other.

---

## Confidence is not geometry, but it travels with it

`OcrLine.confidence` and `OcrWord.confidence` are a `Confidence` value class normalized to
`0.0..1.0`, whatever scale the engine used natively (Tesseract reports `0..100`, Vision
`0..1`). An engine that reports none gives `Confidence.Unknown`, whose `value` is `NaN` — so
check before doing arithmetic:

```kotlin
val c = line.confidence
if (c.isKnown && c.value < 0.6f) flagForReview(line)

// or, with a default
val score = line.confidence.orElse(1f)
```

Text from the digital layer is not a guess and carries `Confidence.Certain`. `Unknown` sorts
below every known value, so `minOf(...)` over a line's words does not silently return `NaN`.
