import { authenticateAdmin } from "./_lib/admin-auth.js";

const ADMIN_PAGE = String.raw`<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>PointIQ Feedback Admin</title>
  <meta name="referrer" content="no-referrer">
  <style>
    :root { color-scheme: dark; font-family: Inter, system-ui, sans-serif; background: #07182f; color: #eef5ff; }
    * { box-sizing: border-box; }
    body { margin: 0; min-width: 320px; background: #07182f; }
    main { width: min(100% - 32px, 860px); margin: 0 auto; padding: 28px 0 48px; }
    h1 { margin: 0; font-size: 22px; font-weight: 600; }
    header { display: flex; align-items: baseline; justify-content: space-between; gap: 16px; margin-bottom: 22px; }
    #status { margin: 0; color: #a9bbd4; font-size: 13px; }
    #entries { display: grid; gap: 12px; }
    article { border-top: 1px solid #203758; padding: 14px 0 2px; }
    article:first-child { border-top: 0; padding-top: 0; }
    .meta { display: flex; flex-wrap: wrap; gap: 6px 14px; margin-bottom: 7px; color: #a9bbd4; font-size: 12px; }
    .category { color: #d7e7ff; font-weight: 600; }
    .description { margin: 0; color: #f3f7fc; white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.45; }
    .email { margin: 8px 0 0; color: #a9bbd4; font-size: 13px; overflow-wrap: anywhere; }
    .attachment { display: block; max-width: min(100%, 520px); max-height: 360px; margin-top: 12px; border-radius: 4px; }
    video.attachment { width: 100%; }
    #load-more { display: block; width: 100%; min-height: 42px; margin-top: 24px; border: 1px solid #41648c; border-radius: 4px; background: transparent; color: #eef5ff; font: inherit; cursor: pointer; }
    #load-more:hover:not(:disabled) { background: #102b4c; }
    #load-more:focus-visible { outline: 2px solid #8fc4ff; outline-offset: 2px; }
    #load-more:disabled { cursor: wait; opacity: .65; }
    .empty { margin: 0; color: #a9bbd4; }
    @media (max-width: 520px) { main { width: min(100% - 24px, 860px); padding-top: 20px; } header { display: block; } #status { margin-top: 7px; } }
  </style>
</head>
<body>
  <main>
    <header>
      <h1>PointIQ Feedback</h1>
      <p id="status" role="status" aria-live="polite">Loading feedback...</p>
    </header>
    <section id="entries" aria-live="polite" aria-label="Submitted feedback"></section>
    <button id="load-more" type="button" hidden>Load more</button>
  </main>
  <script>
    (() => {
      const entries = document.getElementById("entries");
      const loadMore = document.getElementById("load-more");
      const status = document.getElementById("status");
      let cursor = null;
      let loading = false;

      function addValue(parent, className, value) {
        const element = document.createElement("p");
        element.className = className;
        element.textContent = value;
        parent.append(element);
        return element;
      }

      function addEntry(item) {
        const article = document.createElement("article");
        const meta = document.createElement("div");
        meta.className = "meta";
        const date = item.createdAt ? new Date(item.createdAt).toLocaleString() : "Unknown date";
        addValue(meta, "date", date);
        addValue(meta, "category", item.category || "Unknown category");
        addValue(meta, "category-value", item.categoryValue || "Unknown value");
        article.append(meta);
        addValue(article, "description", item.description || "");
        if (item.email) addValue(article, "email", item.email);

        if (item.attachmentUrl) {
          try {
            const url = new URL(item.attachmentUrl, window.location.origin);
            if (url.origin === window.location.origin && (item.attachmentKind === "video" || item.attachmentKind === "image")) {
              const media = document.createElement(item.attachmentKind === "video" ? "video" : "img");
              media.className = "attachment";
              media.src = url.href;
              if (item.attachmentKind === "video") {
                media.controls = true;
                media.preload = "metadata";
              } else {
                media.alt = "Attached feedback image";
                media.loading = "lazy";
              }
              article.append(media);
            }
          } catch {
            // Ignore malformed attachment URLs from an invalid response.
          }
        }
        entries.append(article);
      }

      async function loadPage(reset) {
        if (loading) return;
        loading = true;
        loadMore.disabled = true;
        status.textContent = reset ? "Loading feedback..." : "Loading more...";
        try {
          const url = new URL("/api/admin/feedback", window.location.origin);
          if (!reset && cursor) url.searchParams.set("cursor", cursor);
          const response = await fetch(url.href, { headers: { Accept: "application/json" }, credentials: "same-origin", cache: "no-store" });
          if (!response.ok) throw new Error("feedback_load_failed");
          const data = await response.json();
          if (reset) entries.textContent = "";
          for (const item of Array.isArray(data.items) ? data.items : []) addEntry(item);
          cursor = typeof data.nextCursor === "string" && data.nextCursor ? data.nextCursor : null;
          loadMore.hidden = !cursor;
          if (entries.children.length === 0) {
            const empty = document.createElement("p");
            empty.className = "empty";
            empty.textContent = "No feedback has been submitted.";
            entries.append(empty);
          }
          status.textContent = "";
        } catch {
          status.textContent = "Feedback could not be loaded.";
          loadMore.hidden = true;
        } finally {
          loading = false;
          loadMore.disabled = false;
        }
      }

      loadMore.addEventListener("click", () => loadPage(false));
      loadPage(true);
    })();
  </script>
</body>
</html>`;

function pageHeaders() {
  return {
    "Cache-Control": "no-store",
    "Content-Type": "text/html; charset=utf-8",
    "Content-Security-Policy": "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src 'self'; media-src 'self'; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'",
    "Referrer-Policy": "no-referrer",
    "X-Content-Type-Options": "nosniff",
  };
}

export async function onRequest(context) {
  const auth = await authenticateAdmin(context.request, context.env);
  if (!auth.ok) return new Response("Not authorized.", { status: auth.status, headers: pageHeaders() });
  if (context.request.method !== "GET") {
    return new Response("Method not allowed.", { status: 405, headers: { ...pageHeaders(), Allow: "GET" } });
  }
  return new Response(ADMIN_PAGE, { status: 200, headers: pageHeaders() });
}
