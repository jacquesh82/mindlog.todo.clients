import CoreData
import CoreDesignSystem
import CoreNetwork
import SwiftUI

/// Le tiroir de navigation : vues d'ensemble, projets, étiquettes, filtres.
///
/// Android utilise un `ModalNavigationDrawer`, qui n'a pas d'équivalent iOS et
/// dont l'imitation se remarque tout de suite. La colonne latérale d'un
/// `NavigationSplitView` porte exactement le même contenu et se comporte comme
/// l'utilisateur l'attend : liste puis détail sur iPhone, deux colonnes sur
/// iPad. C'est le seul écart d'interaction assumé du portage.
///
/// Une `List` et non une `ScrollView` de sections empilées : le nombre de
/// projets n'est pas borné, et une pile verticale les construirait tous, y
/// compris ceux qu'on ne voit jamais.
struct NavigationSidebar: View {

    let state: NavigationState
    let selected: TaskView
    let onSelect: (TaskView) -> Void

    var body: some View {
        List {
            Section {
                entry(
                    label: TaskView.today.title,
                    systemImage: "calendar",
                    count: state.ready?.counts.today,
                    isSelected: selected == .today,
                    action: { onSelect(.today) }
                )
                entry(
                    label: TaskView.inbox.title,
                    systemImage: "tray",
                    count: state.ready?.counts.inbox,
                    isSelected: selected == .inbox,
                    action: { onSelect(.inbox) }
                )
                entry(
                    label: TaskView.all.title,
                    systemImage: "list.bullet",
                    count: nil,
                    isSelected: selected == .all,
                    action: { onSelect(.all) }
                )
            }

            switch state {
            // Le tiroir reste utilisable pendant le chargement et même en cas
            // d'échec : les trois vues d'ensemble ci-dessus ne dépendent
            // d'aucune des listes, et les priver de leur badge vaut mieux que de
            // rendre la navigation inaccessible.
            case .loading:
                EmptyView()

            case .failed:
                Text("Could not load projects.")
                    .font(.mindlogBodyMedium)
                    .foregroundStyle(MindlogColor.error)

            case let .ready(ready):
                // La boîte de réception a déjà sa propre entrée en haut ; la
                // relister parmi les projets la ferait apparaître deux fois,
                // sous deux noms différents.
                let projects = ready.projects.filter { !$0.isInbox }

                if !projects.isEmpty {
                    Section("PROJECTS") {
                        ForEach(projects) { project in
                            entry(
                                label: project.name,
                                systemImage: "circle.fill",
                                tint: project.color.flatMap(Color.init(hex:)),
                                count: ready.counts.byProject[project.id],
                                isSelected: selected.id == project.id,
                                action: {
                                    onSelect(.project(id: project.id, title: project.name))
                                }
                            )
                        }
                    }
                }

                if !ready.labels.isEmpty {
                    Section("LABELS") {
                        ForEach(ready.labels) { label in
                            entry(
                                label: label.name,
                                systemImage: "tag",
                                tint: label.color.flatMap(Color.init(hex:)),
                                count: ready.counts.byLabel[label.id],
                                isSelected: selected.id == label.id,
                                action: { onSelect(.label(id: label.id, title: label.name)) }
                            )
                        }
                    }
                }

                if !ready.filters.isEmpty {
                    Section("FILTERS") {
                        ForEach(ready.filters) { filter in
                            entry(
                                label: filter.name,
                                systemImage: "line.3.horizontal.decrease.circle",
                                tint: filter.color.flatMap(Color.init(hex:)),
                                // Pas de compteur : un filtre est une requête
                                // que seul le serveur sait exécuter, le compter
                                // d'ici demanderait une requête par filtre.
                                count: nil,
                                isSelected: selected.id == filter.id,
                                action: { onSelect(.filter(id: filter.id, title: filter.name)) }
                            )
                        }
                    }
                }
            }
        }
        .listStyle(.sidebar)
        .navigationTitle("mindlog todo")
    }

    @ViewBuilder
    private func entry(
        label: String,
        systemImage: String,
        tint: Color? = nil,
        count: Int?,
        isSelected: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack {
                // Un projet ou une étiquette porte une couleur : la pastille la
                // montre, plutôt qu'une icône générique teintée qui la rendrait
                // illisible sur les teintes claires.
                if let tint {
                    Circle()
                        .fill(tint)
                        .frame(width: 12, height: 12)
                        .frame(width: 20)
                } else {
                    Image(systemName: systemImage)
                        .frame(width: 20)
                        .foregroundStyle(MindlogColor.onSurfaceVariant)
                }

                Text(label)
                    .font(.mindlogLabelLarge)
                    .lineLimit(1)

                Spacer()

                if let count, count > 0 {
                    Text("\(count)")
                        .font(.mindlogLabelMedium)
                        .foregroundStyle(MindlogColor.onSurfaceVariant)
                }
            }
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .foregroundStyle(isSelected ? MindlogColor.primary : MindlogColor.onSurface)
        .listRowBackground(
            isSelected ? MindlogColor.secondaryContainer : Color.clear
        )
    }
}
