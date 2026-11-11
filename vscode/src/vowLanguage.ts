// The .vow language in VS Code: one keyword colour per depth (semantic tokens from the shared reader),
// the reader's problems as you type, Ctrl+click on what a vow names, the outline, and a line over each
// vow saying which archvow command changes it. Works without the archvow CLI and in untrusted folders.
import * as fs from 'node:fs';
import * as path from 'node:path';
import * as vscode from 'vscode';
import { Line, VowDocument } from './vow/document';
import { hazardComponent, hazardShortId, linksOf, moduleFiles } from './vow/links';
import { tokenize, TokenKind } from './vow/scanner';
import { componentVow, vowKind } from './layout';

const TOKEN_TYPES = ['vowKey0', 'vowKey1', 'vowKey2', 'vowKey3', 'vowId', 'vowPerson', 'vowDate'];
export const LEGEND = new vscode.SemanticTokensLegend(TOKEN_TYPES, []);

const TYPE_OF: Partial<Record<TokenKind, number>> = {
  [TokenKind.KEY_0]: 0,
  [TokenKind.KEY_1]: 1,
  [TokenKind.KEY_2]: 2,
  [TokenKind.KEY_3]: 3,
  [TokenKind.ID]: 4,
  [TokenKind.PERSON]: 5,
  [TokenKind.DATE]: 6,
};

/** The project folder of a vow: the folder holding its `.archvow/`. */
export function projectOf(file: string): string | null {
  const parts = file.split(/[\\/]/);
  const at = parts.lastIndexOf('.archvow');
  return at <= 0 ? null : parts.slice(0, at).join(path.sep) || path.sep;
}

/** What the line over a vow says: which archvow command changes it. */
export function bannerOf(file: string): string {
  const what = vowKind(file);
  if (what.kind === 'proposals') {
    return 'Decisions the AI proposed - a person reviews each one: archvow decision accept <component> <n>, or archvow decision make <component> <n> "..."';
  }
  if (what.kind === 'component') {
    return `Written by archvow commands - change it with: archvow specify ${what.name} (all of it: archvow show ${what.name}; another feature: archvow component move ${what.name} <feature>)`;
  }
  if (what.kind === 'feature') {
    return `Written by archvow commands - what the product does: archvow show ${what.name}, archvow feature rename ${what.name} <name>, its page: archvow doc ${what.name}`;
  }
  return 'Written by archvow commands (archvow init <package> --force -a <answer>=..., archvow rule add...) - only rules, allowed_paths and dependencies_by_path are edited by hand, by an architect';
}

class SemanticTokens implements vscode.DocumentSemanticTokensProvider {
  provideDocumentSemanticTokens(document: vscode.TextDocument): vscode.SemanticTokens {
    const builder = new vscode.SemanticTokensBuilder(LEGEND);
    for (const token of tokenize(document.getText())) {
      const type = TYPE_OF[token.kind];
      if (type === undefined) continue;
      const start = document.positionAt(token.start);
      builder.push(start.line, start.character, token.end - token.start, type, 0);
    }
    return builder.build();
  }
}

class Links implements vscode.DocumentLinkProvider {
  provideDocumentLinks(document: vscode.TextDocument): vscode.DocumentLink[] {
    const root = projectOf(document.uri.fsPath);
    const doc = VowDocument.parse(document.getText());
    const out: vscode.DocumentLink[] = [];
    for (const line of doc.lines) {
      for (const link of linksOf(line)) {
        const target = root === null ? null : this.target(root, document, doc, link.kind, link.target);
        if (target === null) continue;
        const item = new vscode.DocumentLink(
          new vscode.Range(document.positionAt(link.start), document.positionAt(link.end)), target);
        item.tooltip = link.kind === 'COMPONENT' ? `Open the vow of ${link.target}`
          : link.kind === 'HAZARD' ? `Go to hazard ${link.target}` : `Open ${link.target}`;
        out.push(item);
      }
    }
    return out;
  }

  private target(root: string, document: vscode.TextDocument, doc: VowDocument, kind: string, name: string): vscode.Uri | null {
    if (kind === 'COMPONENT') {
      const file = componentVow(root, name);
      return file === null ? null : vscode.Uri.file(file);
    }
    if (kind === 'MODULE') {
      for (const candidate of moduleFiles(name)) {
        const file = path.join(root, candidate);
        if (fs.existsSync(file)) return vscode.Uri.file(file);
      }
      return null;
    }
    // a hazard: H1 in this vow, clean_orders.H1 in that component's vow
    const component = hazardComponent(name);
    const id = hazardShortId(name);
    const file = component === null ? document.uri.fsPath : componentVow(root, component);
    if (file === null) return null;
    let text: string;
    try {
      text = component === null ? document.getText() : fs.readFileSync(file, 'utf8');
    } catch {
      return null;
    }
    const target = component === null ? doc : VowDocument.parse(text);
    const line = target.roots.find((l) => l.key === 'hazard' && l.word() === id);
    return line === undefined ? null : vscode.Uri.file(file).with({ fragment: `L${line.number}` });
  }
}

class Symbols implements vscode.DocumentSymbolProvider {
  provideDocumentSymbols(document: vscode.TextDocument): vscode.DocumentSymbol[] {
    const doc = VowDocument.parse(document.getText());
    const symbol = (line: Line, depth: number): vscode.DocumentSymbol => {
      const value = line.value ?? '';
      const detail = value.length > 60 ? value.substring(0, 57) + '...' : value;
      const kind = line.children.length > 0
        ? (depth === 0 ? vscode.SymbolKind.Class : vscode.SymbolKind.Object)
        : vscode.SymbolKind.Property;
      const range = new vscode.Range(document.positionAt(line.start), document.positionAt(line.blockEnd()));
      const select = new vscode.Range(document.positionAt(line.keyStart), document.positionAt(line.keyEnd));
      const item = new vscode.DocumentSymbol(line.key ?? '', detail, kind, range, select);
      item.children = line.children.map((c) => symbol(c, depth + 1));
      return item;
    };
    return doc.roots.map((l) => symbol(l, 0));
  }
}

class Banner implements vscode.CodeLensProvider {
  provideCodeLenses(document: vscode.TextDocument): vscode.CodeLens[] {
    const parts = document.uri.fsPath.split(/[\\/]/);
    const component = parts.length >= 3 && parts[parts.length - 3] === 'components' ? parts[parts.length - 2] : null;
    const lens = new vscode.CodeLens(new vscode.Range(0, 0, 0, 0), {
      title: '$(info) ' + bannerOf(document.uri.fsPath),
      command: component === null ? '' : 'archvow.openComponent',
      arguments: component === null ? [] : [component],
      tooltip: component === null ? undefined : `Open the panel of ${component}`,
    });
    return [lens];
  }
}

/** Registers the language features; the reader's problems go to `problems`. */
export function registerVowLanguage(context: vscode.ExtensionContext): void {
  const selector: vscode.DocumentSelector = { language: 'vow' };
  const problems = vscode.languages.createDiagnosticCollection('vow');
  const read = (document: vscode.TextDocument): void => {
    if (document.languageId !== 'vow') return;
    const doc = VowDocument.parse(document.getText());
    problems.set(document.uri, doc.problems.map((p) => {
      const diagnostic = new vscode.Diagnostic(
        new vscode.Range(document.positionAt(p.start), document.positionAt(Math.max(p.end, p.start))),
        p.message,
        p.warning ? vscode.DiagnosticSeverity.Warning : vscode.DiagnosticSeverity.Error,
      );
      diagnostic.source = 'vow';
      return diagnostic;
    }));
  };
  context.subscriptions.push(
    problems,
    vscode.languages.registerDocumentSemanticTokensProvider(selector, new SemanticTokens(), LEGEND),
    vscode.languages.registerDocumentLinkProvider(selector, new Links()),
    vscode.languages.registerDocumentSymbolProvider(selector, new Symbols()),
    vscode.languages.registerCodeLensProvider(selector, new Banner()),
    vscode.workspace.onDidOpenTextDocument(read),
    vscode.workspace.onDidChangeTextDocument((e) => read(e.document)),
    vscode.workspace.onDidCloseTextDocument((d) => problems.delete(d.uri)),
  );
  vscode.workspace.textDocuments.forEach(read);
}
