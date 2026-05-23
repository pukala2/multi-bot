import { render } from 'preact';
import { ChatWidget } from './ChatWidget';
import { readBootConfig, resolveConfig } from './config';
import './widget.css';

async function bootstrap() {
  const boot = readBootConfig();
  const config = await resolveConfig(boot);

  const existing = document.getElementById('multibot-widget-mount');
  if (existing) existing.remove();

  const mount = document.createElement('div');
  mount.id = 'multibot-widget-mount';
  document.body.appendChild(mount);

  render(<ChatWidget config={config} />, mount);
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', () => { void bootstrap(); });
} else {
  void bootstrap();
}
