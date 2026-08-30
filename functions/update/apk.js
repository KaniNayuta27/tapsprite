const GITHUB_APK =
  "https://github.com/KaniNayuta27/tapsprite/releases/latest/download/tapsprite.apk";

export async function onRequest() {
  const src = await fetch(GITHUB_APK, {
    redirect: "follow",
    headers: { "User-Agent": "tapsprite-updater" },
  });
  if (!src.ok || !src.body) {
    return new Response("apk " + src.status, { status: 502 });
  }
  const headers = new Headers();
  headers.set("content-type", "application/vnd.android.package-archive");
  headers.set("content-disposition", "attachment; filename=tapsprite.apk");
  const len = src.headers.get("content-length");
  if (len) {
    headers.set("content-length", len);
  }
  headers.set("cache-control", "public, max-age=300");
  return new Response(src.body, { status: 200, headers });
}
