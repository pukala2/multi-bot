import { describe, it, expect } from 'vitest';
import { ApiError, unwrap } from './errors';

describe('ApiError', () => {
  it('message bierze detail w pierwszej kolejności', () => {
    const e = new ApiError({ title: 'Tytuł', detail: 'Szczegół', status: 400 });
    expect(e.message).toBe('Szczegół');
    expect(e.status).toBe(400);
    expect(e.name).toBe('ApiError');
    expect(e).toBeInstanceOf(Error);
  });

  it('bez detail spada na title', () => {
    expect(new ApiError({ title: 'Tytuł' }).message).toBe('Tytuł');
  });

  it('bez detail i title — komunikat domyślny, status 0', () => {
    const e = new ApiError({});
    expect(e.message).toBe('Unknown API error');
    expect(e.status).toBe(0);
  });
});

describe('unwrap', () => {
  it('zwraca data gdy brak błędu', () => {
    expect(unwrap({ data: { id: 1 } })).toEqual({ id: 1 });
  });

  it('rzuca ApiError gdy jest error', () => {
    expect(() => unwrap({ error: { title: 'Boom', status: 409 } })).toThrowError(ApiError);
    try {
      unwrap({ error: { title: 'Boom', status: 409 } });
    } catch (e) {
      expect((e as ApiError).status).toBe(409);
    }
  });

  it('rzuca ApiError "Empty response" gdy brak data i brak error', () => {
    try {
      unwrap({});
      throw new Error('powinno rzucić');
    } catch (e) {
      expect(e).toBeInstanceOf(ApiError);
      expect((e as ApiError).problem.title).toBe('Empty response');
    }
  });
});
