import { NgZone } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { ApiService } from './api.service';

describe('ApiService', () => {
  let service: ApiService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(ApiService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('consumeTopicMessages', () => {
    let fakeSource: { onmessage?: (e: { data: string }) => void; onerror?: () => void; close: jasmine.Spy };
    let originalEventSource: typeof EventSource;

    beforeEach(() => {
      originalEventSource = window.EventSource;
      fakeSource = { close: jasmine.createSpy('close') };
      (window as any).EventSource = function () { return fakeSource; };
    });

    afterEach(() => {
      (window as any).EventSource = originalEventSource;
    });

    it('delivers stream messages inside the Angular zone so the view updates', () => {
      const zone = TestBed.inject(NgZone);
      const received: { data: string; inZone: boolean }[] = [];
      service.consumeTopicMessages('orders', 'group-1')
        .subscribe(data => received.push({ data, inZone: NgZone.isInAngularZone() }));

      // A real EventSource fires its callbacks outside the zone.
      zone.runOutsideAngular(() => fakeSource.onmessage!({ data: '{"id":1}' }));

      expect(received).toEqual([{ data: '{"id":1}', inZone: true }]);
    });

    it('closes the stream when unsubscribed', () => {
      service.consumeTopicMessages('orders', 'group-1').subscribe().unsubscribe();
      expect(fakeSource.close).toHaveBeenCalled();
    });
  });
});
