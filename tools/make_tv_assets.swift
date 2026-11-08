import AppKit
import Foundation

// Генератор иконки и баннера Android TV для HDrezka TV.
// Запуск: swift tools/make_tv_assets.swift

let outDir = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
    .appendingPathComponent("app/src/main/res/drawable-nodpi")
try? FileManager.default.createDirectory(at: outDir, withIntermediateDirectories: true)

let orangeTop = NSColor(calibratedRed: 1.0, green: 0.60, blue: 0.16, alpha: 1.0)
let orangeBottom = NSColor(calibratedRed: 1.0, green: 0.34, blue: 0.0, alpha: 1.0)

func drawMark(in rect: NSRect, radius: CGFloat) {
    NSGraphicsContext.current?.saveGraphicsState()
    // Оранжевый скруглённый квадрат.
    let square = NSBezierPath(roundedRect: rect, xRadius: radius, yRadius: radius)
    square.addClip()
    NSGradient(colors: [orangeTop, orangeBottom])?.draw(in: square, angle: -55)

    // Белые боковые полосы «H».
    let barWidth = rect.width * 0.135
    let barHeight = rect.height * 0.52
    let barY = rect.midY - barHeight / 2
    let left = NSRect(
        x: rect.minX + rect.width * 0.19,
        y: barY,
        width: barWidth,
        height: barHeight
    )
    let right = NSRect(
        x: rect.maxX - rect.width * 0.19 - barWidth,
        y: barY,
        width: barWidth,
        height: barHeight
    )
    NSColor.white.setFill()
    NSBezierPath(roundedRect: left, xRadius: barWidth / 2, yRadius: barWidth / 2).fill()
    NSBezierPath(roundedRect: right, xRadius: barWidth / 2, yRadius: barWidth / 2).fill()

    // Треугольник Play между полосами.
    let triangle = NSBezierPath()
    let tLeft = rect.minX + rect.width * 0.40
    let tRight = rect.minX + rect.width * 0.66
    let tTop = rect.midY + rect.height * 0.15
    let tBottom = rect.midY - rect.height * 0.15
    triangle.move(to: NSPoint(x: tLeft, y: tBottom))
    triangle.line(to: NSPoint(x: tLeft, y: tTop))
    triangle.line(to: NSPoint(x: tRight, y: rect.midY))
    triangle.close()
    triangle.fill()
    NSGraphicsContext.current?.restoreGraphicsState()
}

func drawIcon(size: CGFloat) -> NSImage {
    let image = NSImage(size: NSSize(width: size, height: size))
    image.lockFocus()
    NSColor(calibratedRed: 0.043, green: 0.043, blue: 0.055, alpha: 1.0).setFill()
    NSRect(x: 0, y: 0, width: size, height: size).fill()

    let margin = size * 0.12
    let rect = NSRect(
        x: margin,
        y: margin,
        width: size - margin * 2,
        height: size - margin * 2
    )
    drawMark(in: rect, radius: size * 0.22)
    image.unlockFocus()
    return image
}

func drawBanner() -> NSImage {
    let width: CGFloat = 640
    let height: CGFloat = 360
    let image = NSImage(size: NSSize(width: width, height: height))
    image.lockFocus()

    let bg = NSGradient(colors: [
        NSColor(calibratedRed: 0.09, green: 0.09, blue: 0.11, alpha: 1.0),
        NSColor(calibratedRed: 0.035, green: 0.035, blue: 0.045, alpha: 1.0)
    ])
    bg?.draw(in: NSRect(x: 0, y: 0, width: width, height: height), angle: -90)

    // Лёгкое оранжевое свечение слева.
    let glow = NSGradient(colors: [
        NSColor(calibratedRed: 1, green: 0.45, blue: 0.05, alpha: 0.22),
        NSColor(calibratedRed: 1, green: 0.45, blue: 0.05, alpha: 0.0)
    ])
    glow?.draw(
        in: NSBezierPath(ovalIn: NSRect(x: -80, y: 20, width: 420, height: 320)),
        relativeCenterPosition: NSPoint(x: 0, y: 0)
    )

    // Знак.
    drawMark(
        in: NSRect(x: 36, y: 96, width: 168, height: 168),
        radius: 40
    )

    // Словоmark: HDREZKA TV.
    let font = NSFont(name: "HelveticaNeue-Bold", size: 50)
        ?? NSFont.systemFont(ofSize: 50, weight: .heavy)
    let smallFont = NSFont(name: "HelveticaNeue-Medium", size: 18)
        ?? NSFont.systemFont(ofSize: 18, weight: .medium)

    let word = NSMutableAttributedString()
    word.append(NSAttributedString(
        string: "HDREZKA",
        attributes: [.font: font, .foregroundColor: NSColor.white]
    ))
    word.append(NSAttributedString(
        string: " TV",
        attributes: [.font: font, .foregroundColor: orangeTop]
    ))
    word.draw(at: NSPoint(x: 232, y: 168))

    let subtitle = NSAttributedString(
        string: "ANDROID TV",
        attributes: [
            .font: smallFont,
            .foregroundColor: NSColor(calibratedWhite: 0.55, alpha: 1.0),
            .kern: 4.0
        ]
    )
    subtitle.draw(at: NSPoint(x: 236, y: 130))

    // Тонкая оранжевая линия снизу.
    orangeTop.setFill()
    NSRect(x: 0, y: 0, width: width, height: 5).fill()

    image.unlockFocus()
    return image
}

func save(_ image: NSImage, name: String) {
    guard let tiff = image.tiffRepresentation,
          let rep = NSBitmapImageRep(data: tiff),
          let data = rep.representation(using: .png, properties: [:]) else {
        fatalError("Не удалось сохранить \(name)")
    }
    let url = outDir.appendingPathComponent(name)
    try! data.write(to: url)
    print("✓ \(url.path)")
}

save(drawIcon(size: 512), name: "ic_launcher.png")
save(drawBanner(), name: "tv_banner.png")
