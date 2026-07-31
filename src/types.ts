/**
 * Types de l'API, vus par le client web.
 *
 * Les formes partagées avec le serveur sont RÉ-EXPORTÉES depuis `@mindlog/core`
 * plutôt que redéclarées ici. Elles l'étaient à la main des deux côtés, et elles
 * avaient déjà divergé sans que rien ne le signale : `Task.completedAt` existait
 * côté serveur et était invisible ici, tandis que `Task.children?` traînait ici
 * sans usage (l'arbre passe par `TreeTask` dans tree.ts).
 *
 * Ce sont des imports TYPE-ONLY : TypeScript les efface à la compilation, donc
 * `@mindlog/core` n'entre PAS dans le bundle (vérifié — taille inchangée, zéro
 * occurrence de code serveur). C'est aussi pourquoi les constantes runtime
 * ci-dessous restent locales : les ré-exporter tirerait core dans le bundle.
 */
import type {
  AiCredits,
  AiLog,
  AiUsage,
  ApiKey,
  ApiKeyCreated,
  Attachment,
  AuthResult,
  CalendarSource,
  DashboardStats,
  Filter,
  Karma,
  Label,
  Notebook,
  NotePage,
  NotePageSummary,
  Project,
  ProjectViewMode,
  PromptKey,
  PromptView,
  Section,
  StorageUsage,
  Task,
  TaskStatus,
  User,
} from '@mindlog/core';

export type {
  AiCredits,
  AiLog,
  AiUsage,
  ApiKey,
  ApiKeyCreated,
  Attachment,
  AuthResult,
  CalendarSource,
  DashboardStats,
  Filter,
  Karma,
  Label,
  Notebook,
  NotePage,
  NotePageSummary,
  Project,
  ProjectViewMode,
  PromptKey,
  PromptView,
  Section,
  StorageUsage,
  Task,
  TaskStatus,
  User,
};

// Valeurs (et non types) : nécessaires au runtime pour les listes déroulantes.
// `satisfies` les contraint aux unions de core — un statut supprimé ou mal
// orthographié devient une erreur de compilation. Un statut AJOUTÉ côté core et
// oublié ici ne l'est pas : c'est la limite de cette garde.
export const TASK_STATUSES = [
  'todo',
  'in_progress',
  'blocked',
  'done',
  'cancelled',
] as const satisfies readonly TaskStatus[];

export const PROJECT_VIEW_MODES = ['list', 'board', 'calendar'] as const satisfies
  readonly ProjectViewMode[];

/* -------------------------------------------------------------------------- */
/* Formes propres au client                                                    */
/*                                                                             */
/* Le serveur a des équivalents sous d'autres noms (TaskSearchHit, NotePageHit, */
/* TaskAskResult, AiSettingsView, QuickAddParse, CalendarEvent). Les unifier    */
/* demande de renommer des deux côtés — à faire, mais séparément.               */
/* -------------------------------------------------------------------------- */

export interface TaskHit extends Task {
  score: number;
}

export interface NoteHit extends NotePageSummary {
  score: number;
}

export interface QuickAddPreview {
  title: string;
  projectName: string | null;
  labelNames: string[];
  priority: number | null;
  dueDate: string | null;
  recurrence: string | null;
  projectId: string | null;
  labelIds: string[];
  newLabelNames: string[];
}

export interface AskResult {
  answer: string;
  sources: Task[];
  noteSources?: { id: string; title: string; notebookId: string }[];
}

export interface ChatModelOption {
  id: string;
  provider: string;
  label: string;
}

export interface ChatProviderOption {
  id: string;
  label: string;
}

export interface AiSettings {
  cloudHosted: boolean;
  provider: string;
  model: string;
  hasKey: boolean;
  providers: ChatProviderOption[];
  models: ChatModelOption[];
  credits: AiCredits | null;
}

export interface ExternalEvent {
  uid: string;
  summary: string;
  start: string;
  end: string | null;
  allDay: boolean;
  sourceId: string;
  sourceName: string;
  color: string | null;
}

/**
 * Deployment metadata from GET /api/v1/version. `authProviders` reports which
 * sign-in paths the server actually has credentials for, so the login page can
 * hide the rest instead of offering routes that 503 or silently do nothing.
 */
export interface VersionInfo {
  version: string;
  buildDate: string;
  authProviders?: {
    mindlogId: boolean;
    google: boolean;
    passwordReset: boolean;
  };
}
