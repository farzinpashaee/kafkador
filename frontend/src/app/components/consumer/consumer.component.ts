import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { Subscription } from 'rxjs';
import { ApiService } from '../../services';

const MAX_VISIBLE_MESSAGES = 200;

@Component({
  selector: 'app-consumer',
  imports: [CommonModule, RouterModule],
  templateUrl: './consumer.component.html',
  styleUrl: './consumer.component.scss'
})
export class ConsumerComponent implements OnInit, OnDestroy {

  groupId = '';
  topic = '';
  messages: string[] = [];
  connected = false;
  error: string | null = null;

  private streamSubscription?: Subscription;

  constructor(private apiService: ApiService, private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    this.groupId = this.route.snapshot.paramMap.get('groupId') ?? '';
    this.topic = this.route.snapshot.paramMap.get('topic') ?? '';
    this.connect();
  }

  ngOnDestroy(): void {
    this.streamSubscription?.unsubscribe();
  }

  connect(): void {
    this.error = null;
    this.connected = true;
    this.streamSubscription = this.apiService.consumeTopicMessages(this.topic, this.groupId).subscribe({
      next: (message) => {
        this.messages = [message, ...this.messages].slice(0, MAX_VISIBLE_MESSAGES);
      },
      error: (err) => {
        this.connected = false;
        this.error = err?.message ?? 'Connection to the message stream was lost.';
      }
    });
  }

  reconnect(): void {
    this.streamSubscription?.unsubscribe();
    this.messages = [];
    this.connect();
  }

  stop(): void {
    this.streamSubscription?.unsubscribe();
    this.connected = false;
    this.router.navigate(['/consumer']);
  }

}
