import { Component, ElementRef, NgZone, OnDestroy, OnInit, ViewChild, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AiAssistantService, ApiService, CommonService } from '../../services';
import { AiChatResponse, AiChatSession, AiMessage, GenericResponse } from '../../models';

interface Segment {
  code: boolean;
  text: string;
}

interface ChatMessage extends AiMessage {
  segments: Segment[];
}

// The Web Speech API isn't in the TypeScript DOM lib and is still vendor-prefixed in Chromium/Safari.
interface SpeechRecognitionLike {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  onresult: ((event: any) => void) | null;
  onerror: ((event: any) => void) | null;
  onend: (() => void) | null;
  start(): void;
  stop(): void;
  abort(): void;
}

function speechRecognitionCtor(): (new () => SpeechRecognitionLike) | null {
  const w = window as any;
  return window.isSecureContext ? (w.SpeechRecognition ?? w.webkitSpeechRecognition ?? null) : null;
}

const SPEECH_ERRORS: Record<string, string> = {
  'not-allowed': 'Microphone access is blocked. Allow it in your browser\'s site settings to use voice input.',
  'service-not-allowed': 'Microphone access is blocked. Allow it in your browser\'s site settings to use voice input.',
  'audio-capture': 'No microphone was found.',
  'network': 'Voice input needs an internet connection: the browser sends the audio to its speech service.'
};

@Component({
  selector: 'app-ai-assistant',
  imports: [CommonModule, FormsModule],
  templateUrl: './ai-assistant.component.html',
  styleUrl: './ai-assistant.component.scss'
})
export class AiAssistantComponent implements OnInit, OnDestroy {

  assistant = inject(AiAssistantService);
  private apiService = inject(ApiService);
  private commonService = inject(CommonService);
  private zone = inject(NgZone);

  private recognition: SpeechRecognitionLike | null = null;
  speechSupported = signal(false);
  listening = signal(false);

  open = signal(false);
  loading = signal(false);
  error = signal<string | null>(null);
  messages = signal<ChatMessage[]>([]);
  draft = '';

  /** The stored conversation being shown; null until the server answers the first question of a new chat. */
  sessionId = signal<string | null>(null);
  view = signal<'chat' | 'history'>('chat');
  sessions = signal<AiChatSession[]>([]);
  historyLoading = signal(false);
  /** Bumped whenever the shown conversation changes, so a late response can't land in a different chat. */
  private conversation = 0;

  @ViewChild('body') body?: ElementRef<HTMLElement>;
  @ViewChild('input') input?: ElementRef<HTMLTextAreaElement>;

  ngOnInit() {
    this.assistant.refresh();
  }

  ngOnDestroy() {
    this.stopListening();
  }

  toggle() {
    this.open.update(v => !v);
    if (this.open()) {
      this.speechSupported.set(speechRecognitionCtor() !== null);
      this.focusInput();
    } else {
      this.stopListening();
    }
  }

  /** Starts a new session; the server creates it when the first question is answered. */
  newChat() {
    this.showConversation(null, []);
    this.draft = '';
    this.focusInput();
  }

  toggleHistory() {
    if (this.view() === 'history') {
      this.view.set('chat');
      this.focusInput();
      return;
    }
    this.stopListening();
    this.error.set(null);
    this.view.set('history');
    this.historyLoading.set(true);
    this.apiService.getAiChatSessions().subscribe({
      next: res => {
        this.sessions.set(res.body?.data ?? []);
        this.historyLoading.set(false);
      },
      error: (res: HttpErrorResponse) => {
        this.error.set(this.commonService.prepareError(res.error?.error, '500', 'The chat history could not be loaded.').message);
        this.historyLoading.set(false);
      }
    });
  }

  openSession(session: AiChatSession) {
    this.error.set(null);
    this.historyLoading.set(true);
    this.apiService.getAiChatSession(session.id).subscribe({
      next: res => {
        const loaded = res.body?.data;
        this.historyLoading.set(false);
        if (!loaded) return;
        this.showConversation(loaded.id, (loaded.messages ?? []).map(m => this.toMessage(m.role, m.content)));
        this.draft = '';
        this.scrollToBottom();
        this.focusInput();
      },
      error: (res: HttpErrorResponse) => {
        this.error.set(this.commonService.prepareError(res.error?.error, '500', 'This chat could not be opened.').message);
        this.historyLoading.set(false);
      }
    });
  }

  private showConversation(sessionId: string | null, messages: ChatMessage[]) {
    this.stopListening();
    this.conversation++;
    this.sessionId.set(sessionId);
    this.messages.set(messages);
    this.loading.set(false);
    this.error.set(null);
    this.view.set('chat');
  }

  onEnter(event: Event) {
    if ((event as KeyboardEvent).shiftKey) return;
    event.preventDefault();
    this.send();
  }

  toggleListening() {
    if (this.listening()) {
      this.stopListening(false);
    } else {
      this.startListening();
    }
  }

  private startListening() {
    const Recognition = speechRecognitionCtor();
    if (!Recognition || this.loading()) return;

    this.error.set(null);
    const base = this.draft.trimEnd();
    const recognition = new Recognition();
    recognition.lang = navigator.language || 'en-US';
    recognition.continuous = true;
    recognition.interimResults = true;

    // Speech callbacks can fire outside Angular's zone, so re-enter it to refresh the view.
    recognition.onresult = (event: any) => this.zone.run(() => {
      let transcript = '';
      for (let i = 0; i < event.results.length; i++) transcript += event.results[i][0].transcript;
      transcript = transcript.trim();
      this.draft = transcript ? (base ? base + ' ' : '') + transcript : base;
    });
    recognition.onerror = (event: any) => this.zone.run(() => {
      const message = SPEECH_ERRORS[event.error];
      if (message) this.error.set(message);
      else if (event.error !== 'no-speech' && event.error !== 'aborted') this.error.set('Voice input failed. Please try again.');
    });
    recognition.onend = () => this.zone.run(() => {
      this.listening.set(false);
      if (this.recognition === recognition) this.recognition = null;
      this.focusInput();
    });

    this.recognition = recognition;
    try {
      recognition.start();
      this.listening.set(true);
    } catch {
      this.recognition = null;
      this.error.set('Voice input failed. Please try again.');
    }
  }

  /** Manual stop keeps late final results; `discard` (send, new chat, history, close) drops anything still in flight. */
  private stopListening(discard = true) {
    const recognition = this.recognition;
    this.recognition = null;
    this.listening.set(false);
    if (!recognition) return;
    if (discard) {
      recognition.onresult = null;
      recognition.onerror = null;
      recognition.onend = null;
      recognition.abort();
    } else {
      recognition.stop();
    }
  }

  send() {
    this.stopListening();
    const text = this.draft.trim();
    if (!text || this.loading()) return;

    this.error.set(null);
    this.messages.update(m => [...m, this.toMessage('user', text)]);
    this.draft = '';
    this.loading.set(true);
    this.scrollToBottom();

    // The server keeps the earlier turns of the session, so only the new question is sent.
    const conversation = this.conversation;
    this.apiService.aiChat(text, this.sessionId()).subscribe({
      next: (res: HttpResponse<GenericResponse<AiChatResponse>>) => {
        if (conversation !== this.conversation) return;
        this.sessionId.set(res.body?.data?.sessionId ?? this.sessionId());
        this.messages.update(m => [...m, this.toMessage('assistant', res.body?.data?.reply ?? '')]);
        this.loading.set(false);
        this.scrollToBottom();
      },
      error: (res: HttpErrorResponse) => {
        if (conversation !== this.conversation) return;
        // Give the question back to the user so they can retry or edit it.
        this.messages.update(m => m.slice(0, -1));
        this.draft = text;
        this.error.set(this.commonService.prepareError(res.error?.error, '500', 'The assistant could not answer. Please try again.').message);
        this.loading.set(false);
        this.scrollToBottom();
      }
    });
  }

  private toMessage(role: AiMessage['role'], content: string): ChatMessage {
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
