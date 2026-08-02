import SwiftUI
import UIKit

// Tonal ramp grown from the two brand anchors the web client already uses
// (`web/src/styles.css`): #8F621E in light, #EDBA6E in dark. Both sit at hue
// ~36°, so the ramp is one hue at varying tone rather than two palettes. These
// are the same values as `core/designsystem/.../Color.kt` on Android — if one
// moves, both move.

private extension UIColor {
    static let amber10 = UIColor(hex: 0x2E1C00)
    static let amber20 = UIColor(hex: 0x4A2F00)
    static let amber30 = UIColor(hex: 0x6A4600)
    static let amber40 = UIColor(hex: 0x8F621E) // brand, light
    static let amber60 = UIColor(hex: 0xC08F3F)
    static let amber80 = UIColor(hex: 0xEDBA6E) // brand, dark
    static let amber90 = UIColor(hex: 0xFFDDB0)
    static let amber95 = UIColor(hex: 0xFFEEDC)

    // A muted companion for secondary surfaces — same hue, low chroma, so it
    // reads as a neutral without going grey.
    static let neutral10 = UIColor(hex: 0x1F1B16)
    static let neutral20 = UIColor(hex: 0x35302A)
    static let neutral30 = UIColor(hex: 0x4C463F)
    static let neutral50 = UIColor(hex: 0x7E7667)
    static let neutral80 = UIColor(hex: 0xD1C5B4)
    static let neutral90 = UIColor(hex: 0xEDE0CF)
    static let neutral95 = UIColor(hex: 0xFBEFDE)
    static let neutral99 = UIColor(hex: 0xFFFBFF)

    static let red20 = UIColor(hex: 0x690005)
    static let red40 = UIColor(hex: 0xBA1A1A)
    static let red80 = UIColor(hex: 0xFFB4AB)
    static let red90 = UIColor(hex: 0xFFDAD6)
}

/// The product palette, fixed.
///
/// The roles are Material's, because the Android client's screens are written
/// against them and keeping the names identical is what lets the two be read
/// side by side. What changes is the mechanism: Material resolves light and dark
/// through two `ColorScheme` objects, UIKit through one dynamic colour per role,
/// so there is a single list here instead of two.
///
/// No system accent, no wallpaper tinting: the amber *is* the product's
/// identity — the same colour the web client and the marketing site use.
public enum MindlogColor {

    public static let primary = dynamic(light: .amber40, dark: .amber80)

    /// Counter-intuitive but correct in dark: the dark theme's primary is the
    /// *light* amber, so what sits on it must be dark (7.4:1). In light, white
    /// on #8F621E is 5.3:1 — above the 4.5:1 body-text threshold. The web client
    /// reaches the same conclusion by luminance in `web/src/theme.ts`, where the
    /// token is called `--color-brand-ink`.
    public static let onPrimary = dynamic(light: .white, dark: .amber20)

    public static let primaryContainer = dynamic(light: .amber90, dark: .amber30)
    public static let onPrimaryContainer = dynamic(light: .amber10, dark: .amber90)

    public static let secondary = dynamic(light: .neutral30, dark: .neutral80)
    public static let secondaryContainer = dynamic(light: .neutral90, dark: .neutral30)
    public static let onSecondaryContainer = dynamic(light: .neutral10, dark: .neutral90)

    public static let tertiary = dynamic(light: .amber30, dark: .amber60)
    public static let tertiaryContainer = dynamic(light: .amber95, dark: .amber20)

    public static let error = dynamic(light: .red40, dark: .red80)
    public static let onError = dynamic(light: .white, dark: .red20)
    public static let errorContainer = dynamic(light: .red90, dark: UIColor(hex: 0x93000A))
    public static let onErrorContainer = dynamic(light: .red20, dark: .red90)

    public static let background = dynamic(light: .neutral99, dark: .neutral10)
    public static let onBackground = dynamic(light: .neutral10, dark: .neutral90)
    public static let surface = dynamic(light: .neutral99, dark: .neutral10)
    public static let onSurface = dynamic(light: .neutral10, dark: .neutral90)
    public static let surfaceVariant = dynamic(light: .neutral90, dark: .neutral30)
    public static let onSurfaceVariant = dynamic(light: .neutral30, dark: .neutral80)
    public static let outline = dynamic(light: .neutral50, dark: .neutral50)
    public static let outlineVariant = dynamic(light: .neutral80, dark: .neutral30)

    /// Priority accents, matching the web client's and the Android client's task
    /// rows. 1 = urgent.
    public static let priority: [Int: Color] = [
        1: Color(uiColor: UIColor(hex: 0xD1453B)),
        2: Color(uiColor: UIColor(hex: 0xEB8909)),
        3: Color(uiColor: UIColor(hex: 0x246FE0)),
        4: Color(uiColor: UIColor(hex: 0x808080)),
    ]

    private static func dynamic(light: UIColor, dark: UIColor) -> Color {
        Color(uiColor: UIColor { $0.userInterfaceStyle == .dark ? dark : light })
    }
}

public extension Color {
    /// `#rrggbb` → couleur. Le serveur valide déjà ce format pour les projets et
    /// les étiquettes, mais une valeur inattendue ne doit pas faire tomber
    /// l'écran : l'appelant retombe alors sur l'icône générique.
    init?(hex: String) {
        var value = hex.trimmingCharacters(in: .whitespacesAndNewlines)
        if value.hasPrefix("#") { value.removeFirst() }
        guard value.count == 6, let rgb = UInt32(value, radix: 16) else { return nil }
        self.init(uiColor: UIColor(hex: rgb))
    }
}

private extension UIColor {
    convenience init(hex: UInt32) {
        self.init(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: 1
        )
    }
}
