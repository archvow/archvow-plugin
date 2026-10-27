// English or French for what the extension says (archvow's own words stay as archvow prints them).
// The same choice as the IntelliJ plugin: follow the editor, or English, or French.

export type LanguageMode = 'auto' | 'en' | 'fr';

/** Whether to speak French: the setting, else the editor's display language. */
export function isFrench(mode: LanguageMode | string | undefined, editorLanguage: string): boolean {
  if (mode === 'fr') return true;
  if (mode === 'en') return false;
  return editorLanguage.toLowerCase().startsWith('fr');
}

/** `tr(fr, "English", "Français")`. */
export function tr(fr: boolean, english: string, french: string): string {
  return fr ? french : english;
}
