import SwiftUI

/// Typographie et formes.
///
/// Le client Android fige des tailles en `sp` (22, 16, 14). Ici les styles sont
/// **relatifs** aux styles système : c'est la seule façon pour qu'un utilisateur
/// qui a agrandi le texte dans Réglages voie l'application suivre, et sur iOS
/// c'est une attente, pas une option. Les proportions restent celles du client
/// Android — titre plus lourd, corps neutre, libellé légèrement appuyé.
public extension Font {
    static let mindlogTitleLarge = Font.system(.title2, design: .default, weight: .semibold)
    static let mindlogTitleMedium = Font.system(.headline, design: .default, weight: .semibold)
    static let mindlogBodyLarge = Font.system(.body)
    static let mindlogBodyMedium = Font.system(.subheadline)
    static let mindlogLabelLarge = Font.system(.subheadline, design: .default, weight: .medium)
    static let mindlogLabelMedium = Font.system(.caption, design: .default, weight: .medium)
}

/// Rayons de coin, repris tels quels du client Android.
public enum MindlogShape {
    public static let extraSmall: CGFloat = 4
    public static let small: CGFloat = 8
    public static let medium: CGFloat = 12
    public static let large: CGFloat = 16
    public static let extraLarge: CGFloat = 28
}

public extension View {
    /// À appliquer une fois, à la racine.
    ///
    /// Il n'y a pas d'équivalent de `MaterialTheme` en SwiftUI : la couleur
    /// d'accent se propage par l'environnement (`tint`), le reste est appliqué
    /// par les vues elles-mêmes. Ce modificateur ne fait donc que ce qui est
    /// vraiment global.
    func mindlogTheme() -> some View {
        tint(MindlogColor.primary)
            .background(MindlogColor.background)
    }
}
