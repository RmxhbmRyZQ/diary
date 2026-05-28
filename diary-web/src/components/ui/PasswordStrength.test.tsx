import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import PasswordStrength, { evaluateStrength } from './PasswordStrength';

describe('PasswordStrength', () => {
  describe('evaluateStrength', () => {
    it('should return empty for empty password', () => {
      const result = evaluateStrength('');
      expect(result.level).toBe('weak');
      expect(result.label).toBe('');
    });

    it('should return weak for short password', () => {
      const result = evaluateStrength('Ab1');
      expect(result.level).toBe('weak');
      expect(result.label).toContain('至少 8 位');
    });

    it('should return weak for 8 chars missing 2 categories', () => {
      const result = evaluateStrength('abcdefgh');
      expect(result.level).toBe('weak');
    });

    it('should return weak for 8 chars with only lowercase', () => {
      const result = evaluateStrength('abcdefgh');
      expect(result.level).toBe('weak');
    });

    it('should return medium for 8+ chars with upper+lower', () => {
      const result = evaluateStrength('Abcdefgh');
      expect(result.level).toBe('medium');
    });

    it('should return medium for 8+ chars with lower+digit', () => {
      const result = evaluateStrength('abcdefg1');
      expect(result.level).toBe('medium');
    });

    it('should return strong for 8+ chars with upper+lower+digit', () => {
      const result = evaluateStrength('Abcdefg1');
      expect(result.level).toBe('strong');
      expect(result.label).toBe('强');
    });

    it('should return strong for long complex password', () => {
      const result = evaluateStrength('MySecurePassword123!@#');
      expect(result.level).toBe('strong');
    });
  });

  describe('component', () => {
    it('should render nothing for empty password', () => {
      const { container } = render(<PasswordStrength password="" />);
      expect(container.firstChild).toBeNull();
    });

    it('should render weak label for short password', () => {
      render(<PasswordStrength password="abc" />);
      expect(screen.getByText(/至少 8 位/)).toBeTruthy();
    });

    it('should render medium label', () => {
      render(<PasswordStrength password="Abcdefgh" />);
      expect(screen.getByText(/中/)).toBeTruthy();
    });

    it('should render strong label', () => {
      render(<PasswordStrength password="Abc12345" />);
      expect(screen.getByText('强')).toBeTruthy();
    });
  });
});
