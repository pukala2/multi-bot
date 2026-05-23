import { toast } from 'sonner';

export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
}

export class ApiError extends Error {
  readonly problem: ProblemDetail;
  readonly status: number;

  constructor(problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? 'Unknown API error');
    this.name = 'ApiError';
    this.problem = problem;
    this.status = problem.status ?? 0;
  }
}

export function unwrap<TData>(result: { data?: TData; error?: unknown }): TData {
  if (result.error !== undefined) {
    throw new ApiError(result.error as ProblemDetail);
  }
  if (result.data === undefined) {
    throw new ApiError({ title: 'Empty response', status: 0 });
  }
  return result.data;
}

export function showApiError(error: unknown, fallback = 'Wystąpił błąd'): void {
  if (error instanceof ApiError) {
    toast.error(error.problem.title ?? fallback, {
      description: error.problem.detail,
    });
    return;
  }
  if (error instanceof Error) {
    toast.error(fallback, { description: error.message });
    return;
  }
  toast.error(fallback);
}
