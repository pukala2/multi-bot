import { useMutation, useQueryClient } from '@tanstack/react-query';
import { readCookie } from '@/api/client';
import { ApiError, type ProblemDetail } from '@/api/errors';
import type { components } from '@/api/schema';

type DocumentResponse = components['schemas']['DocumentResponse'];

interface UploadVariables {
  file: File;
  versionId?: string;
  onProgress?: (percent: number) => void;
}

function uploadWithProgress(
  file: File,
  versionId: string | undefined,
  onProgress?: (percent: number) => void
): Promise<DocumentResponse> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    const form = new FormData();
    form.append('file', file);

    const url = versionId
      ? `/api/documents?versionId=${encodeURIComponent(versionId)}`
      : '/api/documents';
    xhr.open('POST', url);
    xhr.withCredentials = true;
    // CSRF: raw XHR pomija middleware api clienta — token trzeba dodać ręcznie, inaczej 403.
    const csrf = readCookie('XSRF-TOKEN');
    if (csrf) xhr.setRequestHeader('X-XSRF-TOKEN', csrf);

    xhr.upload.addEventListener('progress', (e) => {
      if (!e.lengthComputable || !onProgress) return;
      onProgress(Math.round((e.loaded / e.total) * 100));
    });

    xhr.addEventListener('load', () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        try {
          resolve(JSON.parse(xhr.responseText) as DocumentResponse);
        } catch {
          reject(new ApiError({ title: 'Invalid JSON', status: xhr.status }));
        }
        return;
      }
      let problem: ProblemDetail = { status: xhr.status, title: `HTTP ${xhr.status}` };
      try {
        problem = JSON.parse(xhr.responseText) as ProblemDetail;
      } catch {
        // keep default
      }
      reject(new ApiError(problem));
    });

    xhr.addEventListener('error', () =>
      reject(new ApiError({ title: 'Network error', status: 0 }))
    );

    xhr.send(form);
  });
}

export function useUploadDocument() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ file, versionId, onProgress }: UploadVariables) =>
      uploadWithProgress(file, versionId, onProgress),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['documents'] });
    },
  });
}
