import { Component, ElementRef, OnInit, ViewChild, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AiAssistantService, ApiService, CommonService } from '../../services';
import { AiChatResponse, AiMessage, GenericResponse } from '../../models';

interface Segment {
  code: boolean;
  text: string;
}

interface ChatMessage extends AiMessage {
  segments: Segment[];
}

const MAX_HISTORY = 20;

@Component({
  selector: 'app-ai-assistant',
  imports: [CommonModule, FormsModule],
  templateUrl: './ai-assistant.component.html',
  styleUrl: './ai-assistant.component.scss'
})
export class AiAssistantComponent implements OnInit {

  assistant = inject(AiAssistantService);
  private apiService = inject(ApiService);
  private commonService = inject(CommonService);

  open = signal(false);
  loading = signal(false);
  error = signal<string | null>(null);
  messages = signal<ChatMessage[]>([]);
  draft = '';

  @ViewChild('body') body?: ElementRef<HTMLElement>;
  @ViewChild('input') input?: ElementRef<HTMLTextAreaElement>;

  ngOnInit() {
    this.assistant.refresh();
  }

  toggle() {
    this.open.update(v => !v);
    if (this.open()) this.focusInput();
  }

  reset() {
    this.messages.set([]);
    this.error.set(null);
    this.focusInput();
  }

  onEnter(event: Event) {
    if ((event as KeyboardEvent).shiftKey) return;
    event.preventDefault();
    this.send();
  }

  send() {
    const text = this.draft.trim();
    if (!text || this.loading()) return;

    this.error.set(null);
    this.messages.update(m => [...m, this.toMessage('user', text)]);
    this.draft = '';
    this.loading.set(true);
    this.scrollToBottom();

    const history: AiMessage[] = this.messages()
      .slice(-MAX_HISTORY)
      .map(m => ({ role: m.role, content: m.content }));

    this.apiService.aiChat(history).subscribe({
      next: (res: HttpResponse<GenericResponse<AiChatResponse>>) => {
        this.messages.update(m => [...m, this.toMessage('assistant', res.body?.data?.reply ?? '')]);
        this.loading.set(false);
        this.scrollToBottom();
      },
      error: (res: HttpErrorResponse) => {
        // Give the question back to the user so they can retry or edit it.
        this.messages.update(m => m.slice(0, -1));
        this.draft = text;
        this.error.set(this.commonService.prepareError(res.error?.error, '500', 'The assistant could not answer. Please try again.').message);
        this.loading.set(false);
        this.scrollToBottom();
      }
    });
  }

  private toMessage(role: 'user' | 'assistant', content: string): ChatMessage {
    return { role, content, segments: this.toSegments(content) };
  }

  /** Splits ``` fenced blocks from plain text so code renders monospaced without any HTML injection. */
  private toSegments(content: string): Segment[] {
    return content.split('```').flatMap((part, index) => {
      const code = index % 2 === 1;
      const text = code ? part.replace(/^[\w+-]*\n/, '').replace(/\n$/, '') : part.trim();
      return text ? [{ code, text }] : [];
    });
  }

  private focusInput() {
    setTimeout(() => this.input?.nativeElement.focus());
  }

  private scrollToBottom() {
    setTimeout(() => {
      const el = this.body?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    });
  }

}
