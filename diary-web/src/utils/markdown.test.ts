import { describe, it, expect } from 'vitest';
import { renderMarkdownToHtml } from './markdown';

describe('renderMarkdownToHtml', () => {
  it('renders a safe https link', () => {
    const html = renderMarkdownToHtml('[example](https://example.com)');
    expect(html).toContain('href="https://example.com"');
  });

  it('renders a safe http link', () => {
    const html = renderMarkdownToHtml('[example](http://example.com)');
    expect(html).toContain('href="http://example.com"');
  });

  it('renders a mailto link', () => {
    const html = renderMarkdownToHtml('[email](mailto:test@example.com)');
    expect(html).toContain('href="mailto:test@example.com"');
  });

  it('renders a relative link', () => {
    const html = renderMarkdownToHtml('[home](/home)');
    expect(html).toContain('href="/home"');
  });

  it('renders an anchor link', () => {
    const html = renderMarkdownToHtml('[top](#top)');
    expect(html).toContain('href="#top"');
  });

  it('strips javascript: URL and renders plain text', () => {
    const html = renderMarkdownToHtml('[click me](javascript:alert(1))');
    expect(html).not.toContain('href="javascript:');
    expect(html).not.toContain('javascript:alert');
    expect(html).toContain('click me');
  });

  it('strips data: URL and renders plain text', () => {
    const html = renderMarkdownToHtml('[xss](data:text/html,<script>alert(1)</script>)');
    expect(html).not.toContain('href="data:');
    expect(html).not.toContain('data:text/html');
  });

  it('strips vbscript: URL and renders plain text', () => {
    const html = renderMarkdownToHtml('[xss](vbscript:msgbox(1))');
    expect(html).not.toContain('href="vbscript:');
  });

  it('escapes double quote in URL', () => {
    const html = renderMarkdownToHtml('[link](https://x.com?q="foo")');
    expect(html).not.toContain('q=""');
    expect(html).toContain('q=&quot;foo&quot;');
  });
});
