package today.mindlog.todo;

import android.os.Bundle;
import android.webkit.WebView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.getcapacitor.BridgeActivity;

import java.util.Locale;

/**
 * Coquille Capacitor + report des inserts système au CSS.
 *
 * Depuis Android 15 (targetSdk 35) l'edge-to-edge est imposé : la fenêtre
 * s'étend sous la barre d'état et sous la barre de navigation, et la WebView
 * dessine dessous — le contenu passait donc sous l'heure en haut et sous la
 * barre de navigation en bas.
 *
 * On ne rogne PAS la WebView côté natif : le fond de l'app dépend du thème
 * clair/sombre choisi DANS la page (préférence web, pas système), donc les
 * bandes ainsi dégagées auraient été peintes d'une couleur native forcément
 * fausse la moitié du temps. On publie plutôt les inserts en variables CSS et
 * la page s'en sert comme padding : elle peint tout, jusqu'aux bords.
 *
 * `env(safe-area-inset-*)` ne suffit pas ici : dans une WebView Android il ne
 * couvre de façon fiable que les découpes d'écran, pas les barres système.
 */
public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final WebView webView = getBridge().getWebView();

        ViewCompat.setOnApplyWindowInsetsListener(webView, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            // Les inserts arrivent en pixels physiques ; le CSS raisonne en px
            // logiques, d'où la division par la densité.
            float density = getResources().getDisplayMetrics().density;
            String js = String.format(Locale.US,
                    "document.documentElement.style.setProperty('--android-inset-top','%.0fpx');"
                            + "document.documentElement.style.setProperty('--android-inset-right','%.0fpx');"
                            + "document.documentElement.style.setProperty('--android-inset-bottom','%.0fpx');"
                            + "document.documentElement.style.setProperty('--android-inset-left','%.0fpx');",
                    bars.top / density, bars.right / density,
                    bars.bottom / density, bars.left / density);
            webView.evaluateJavascript(js, null);
            // Non consommés : rotation et ouverture du clavier doivent continuer
            // à produire de nouveaux inserts.
            return windowInsets;
        });

        // Le premier passage d'inserts peut précéder le chargement de la page :
        // on en redemande un une fois le document en place.
        webView.postDelayed(() -> ViewCompat.requestApplyInsets(webView), 500);
    }
}
