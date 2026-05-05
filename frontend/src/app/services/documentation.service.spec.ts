import { TestBed } from '@angular/core/testing';
import { DocumentationService } from './documentation.service';

describe('DocumentationService', () => {
  let service: DocumentationService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(DocumentationService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should include the documentation text in the output', () => {
    const result = service.createDocumentationHtml(
      'Max message size in bytes',
      'https://kafka.apache.org/documentation/#brokerconfigs_max.message.bytes'
    );
    expect(result).toContain('Max message size in bytes');
  });

  it('should include the documentation link in the output', () => {
    const result = service.createDocumentationHtml('Some doc', 'https://kafka.apache.org/docs');
    expect(result).toContain('https://kafka.apache.org/docs');
  });

  // ---- Security tests (tracked in issue #15: XSS via [innerHTML]) ----
  // These are marked pending until createDocumentationHtml is refactored
  // to return structured data instead of a raw HTML string.

  it('[issue-15] should not render script tags from broker-supplied documentation', () => {
    pending('Fix tracked in GitHub issue #15 — DocumentationService XSS via [innerHTML]');
    const maliciousDoc = '<script>alert("xss")</script>';
    const result = service.createDocumentationHtml(maliciousDoc, 'https://kafka.apache.org');
    expect(result).not.toContain('<script>alert');
  });

  it('[issue-15] should not render javascript: protocol links', () => {
    pending('Fix tracked in GitHub issue #15 — DocumentationService XSS via [innerHTML]');
    const maliciousLink = 'javascript:alert("xss")';
    const result = service.createDocumentationHtml('Some doc', maliciousLink);
    expect(result).not.toContain('href="javascript:');
  });
});
