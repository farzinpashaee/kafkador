import { NgZone } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { ApiService } from './api.service';
import { TopicRecord } from '../models';

describe('ApiService', () => {
  let service: ApiService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(ApiService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('listenTopicRecords', () => {
    type Listener = (event: { data: string }) => void;
    let fakeSource: { url?: string; listeners: Record<string, Listener>; onerror?: () => void; close: jasmine.Spy;
      addEventListener: (name: string, listener: Listener) => void };
    let originalEventSource: typeof EventSource;
    const options = { partitions: [0, 2], keySerde: 'STRING' as const, valueSerde: 'JSON' as const,
      filters: [{ field: 'KEY' as const, operator: 'EQUALS' as const, value: '42' }] };

    beforeEach(() => {
      originalEventSource = window.EventSource;
      fakeSource = {
        listeners: {},
        close: jasmine.createSpy('close'),
        addEventListener(name, listener) { this.listeners[name] = listener; }
      };
      (window as any).EventSource = function (url: string) { fakeSource.url = url; return fakeSource; };
    });

    afterEach(() => {
      (window as any).EventSource = originalEventSource;
    });

    it('sends the partitions, serdes and filters as query parameters', () => {
      service.listenTopicRecords('orders', options).subscribe();
      const params = new URL(fakeSource.url!, 'http://x').searchParams;
      expect(params.get('partitions')).toBe('0,2');
      expect(params.get('valueSerde')).toBe('JSON');
      expect(JSON.parse(params.get('filters')!)).toEqual(options.filters);
    });

    it('delivers record batches inside the Angular zone so the view updates', () => {
      const zone = TestBed.inject(NgZone);
      const received: { batch: TopicRecord[]; inZone: boolean }[] = [];
      service.listenTopicRecords('orders', options)
        .subscribe(batch => received.push({ batch, inZone: NgZone.isInAngularZone() }));

      // A real EventSource fires its callbacks outside the zone.
      zone.runOutsideAngular(() => fakeSource.listeners['records']({ data: '[{"offset":7}]' }));

      expect(received).toEqual([{ batch: [{ offset: 7 } as TopicRecord], inZone: true }]);
    });

    it('errors with the server message on a failure event and closes the stream', () => {
      let message = '';
      service.listenTopicRecords('orders', options).subscribe({ error: (e: Error) => message = e.message });
      fakeSource.listeners['failure']({ data: '{"message":"Topic \'orders\' does not exist"}' });
      expect(message).toBe("Topic 'orders' does not exist");
      expect(fakeSource.close).toHaveBeenCalled();
    });

    it('closes the stream when unsubscribed', () => {
      service.listenTopicRecords('orders', options).subscribe().unsubscribe();
      expect(fakeSource.close).toHaveBeenCalled();
    });
  });
});
