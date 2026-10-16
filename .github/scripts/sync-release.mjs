/* 部署前把官网里的下载信息刷成最新的发行版：版本号、体积、APK 直链。
   这样发新版之后不用手改页面 —— CI 会顺手更新。

   用法（仓库根目录）：node .github/scripts/sync-release.mjs
   GITHUB_REPOSITORY 由 Actions 自动提供；本地跑可以用 GH_TOKEN 提高 API 限额。 */

import { readFile, writeFile } from 'node:fs/promises';

const repo = process.env.GITHUB_REPOSITORY || 'yuedaochangmendian/jianji-note';
const page = new URL('../../website/index.html', import.meta.url);

const headers = { Accept: 'application/vnd.github+json' };
if (process.env.GH_TOKEN) headers.Authorization = `Bearer ${process.env.GH_TOKEN}`;

const releasePage = `https://github.com/${repo}/releases/latest`;
let url = releasePage; // 拿不到直链时退回发行页，永远不会 404
let tag = '';
let size = '';

const res = await fetch(`https://api.github.com/repos/${repo}/releases/latest`, { headers });
if (!res.ok) {
  console.log(`拿不到最新发行版（HTTP ${res.status}），下载按钮退回发行页`);
} else {
  const release = await res.json();
  const assets = Array.isArray(release.assets) ? release.assets : [];
  const apk =
    assets.find((item) => /release\.apk$/i.test(item.name)) ||
    assets.find((item) => /\.apk$/i.test(item.name));

  if (apk && release.tag_name) {
    url = apk.browser_download_url;
    tag = release.tag_name;
    size = (apk.size / 1048576).toFixed(1) + ' MB';
  } else {
    console.log('最新发行版里没有 APK 资产，下载按钮退回发行页');
  }
}

const before = await readFile(page, 'utf8');

let after = before.replace(/<a\b[^>]*data-apk-link[^>]*>/g, (anchor) =>
  anchor.replace(/href="[^"]*"/, `href="${url}"`)
);
if (tag) after = after.replace(/(<span data-apk-version>)[^<]*(<\/span>)/g, `$1${tag}$2`);
if (size) after = after.replace(/(<span data-apk-size>)[^<]*(<\/span>)/g, `$1${size}$2`);

await writeFile(page, after);
console.log(
  after === before
    ? `页面已是最新：${url}`
    : `已更新下载链接为 ${url}${tag ? ` · ${tag}${size ? ` · ${size}` : ''}` : ''}`
);
