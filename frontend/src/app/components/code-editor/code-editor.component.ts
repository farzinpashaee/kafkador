import { Component, ElementRef, EventEmitter, Input, Output, ViewChild } from '@angular/core';

/**
 * A plain-text editor with a line-number gutter, for keys, values and headers. Lines don't wrap, so each
 * number stays level with its line; long lines scroll sideways and the gutter follows vertical scrolling.
 */
@Component({
  selector: 'app-code-editor',
  templateUrl: './code-editor.component.html',
  styleUrl: './code-editor.component.scss'
})
export class CodeEditorComponent {

  @Input() value = '';
  @Output() valueChange = new EventEmitter<string>();
  /** Lines shown before the editor scrolls. */
  @Input() rows = 3;
  @Input() inputId?: string;
  @Input() placeholder = '';
  @Input() invalid = false;

  @ViewChild('gutter', { static: true }) private gutter!: ElementRef<HTMLElement>;

  get lineNumbers(): number[] {
    return Array.from({ length: Math.max(1, (this.value ?? '').split('\n').length) }, (_, i) => i + 1);
  }

  onInput(text: string): void {
    this.value = text;
    this.valueChange.emit(text);
  }

  onScroll(textarea: HTMLTextAreaElement): void {
    this.gutter.nativeElement.scrollTop = textarea.scrollTop;
  }

  /** Tab indents with two spaces instead of moving focus; Shift+Tab still leaves the editor. */
  onKeydown(event: KeyboardEvent, textarea: HTMLTextAreaElement): void {
    if (event.key !== 'Tab' || event.shiftKey) return;
    event.preventDefault();
    const { selectionStart, selectionEnd, value } = textarea;
    this.onInput(value.slice(0, selectionStart) + '  ' + value.slice(selectionEnd));
    textarea.value = this.value;
    textarea.selectionStart = textarea.selectionEnd = selectionStart + 2;
  }

}
