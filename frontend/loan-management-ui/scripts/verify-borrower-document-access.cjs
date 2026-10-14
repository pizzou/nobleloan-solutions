const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const track = fs.readFileSync(path.join(root, 'app/(site)/track/page.tsx'), 'utf8');
const uploadPanel = fs.readFileSync(path.join(root, 'components/DocumentUploadPanel.tsx'), 'utf8');
const helper = fs.readFileSync(path.join(root, 'services/publicDocumentContent.ts'), 'utf8');

assert.match(track, /openApplicantDocument\(url, mode, doc\.fileName\)/,
  'Track page must use the shared document access helper');
assert.match(uploadPanel, /openApplicantDocument\(url, mode, doc\.fileName\)/,
  'Document upload panel must use the shared document access helper');
assert.doesNotMatch(track, /window\.open\(url,\s*["']_blank["'],\s*["']noopener,noreferrer["']\)/,
  'Track page must not use the noopener window.open pattern that returns null in browsers');
assert.doesNotMatch(uploadPanel, /window\.open\(url,\s*["']_blank["'],\s*["']noopener,noreferrer["']\)/,
  'Document upload panel must not use the noopener window.open pattern that returns null in browsers');
assert.match(helper, /if \(!response\.ok\) throw await readableFailure\(response\)/,
  'Document helper must surface API failures instead of treating error bodies as files');
assert.match(helper, /credentials:\s*["']same-origin["']/,
  'Document helper must use the same-origin API proxy');
assert.match(helper, /previewWindow\.opener = null/,
  'Preview tab must be detached from the opener to prevent reverse tabnabbing');
console.log('Borrower document access regression checks passed.');
