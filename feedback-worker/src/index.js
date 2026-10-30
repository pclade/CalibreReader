const copy = {
  en: {
    title: "Calibre To Go feedback",
    intro: "Send a requirement, feature request, bug report, or other feedback.",
    type: "Type",
    feature: "Feature request",
    requirement: "Requirement",
    bug: "Bug",
    other: "Other",
    subject: "Subject",
    message: "Message",
    privacy: "Do not include passwords, access tokens, private library data, or other sensitive information.",
    submit: "Send feedback",
    success: "Thank you. Your feedback has been submitted.",
    rate: "Too many requests. Please try again later.",
    invalid: "Please check the form and try again.",
    failed: "The feedback could not be submitted. Please try again later."
  },
  de: {
    title: "Calibre To Go Feedback",
    intro: "Sende eine Anforderung, einen Funktionswunsch, einen Fehlerbericht oder anderes Feedback.",
    type: "Art",
    feature: "Funktionswunsch",
    requirement: "Anforderung",
    bug: "Fehler",
    other: "Sonstiges",
    subject: "Betreff",
    message: "Nachricht",
    privacy: "Bitte keine Passwörter, Zugriffstoken, privaten Bibliotheksdaten oder andere vertrauliche Informationen eingeben.",
    submit: "Feedback senden",
    success: "Danke. Dein Feedback wurde übermittelt.",
    rate: "Zu viele Anfragen. Bitte später erneut versuchen.",
    invalid: "Bitte Formular prüfen und erneut versuchen.",
    failed: "Das Feedback konnte nicht übermittelt werden. Bitte später erneut versuchen."
  },
  fr: {
    title: "Commentaires Calibre To Go",
    intro: "Envoyez une exigence, une suggestion de fonctionnalité, un rapport de bug ou un autre commentaire.",
    type: "Type",
    feature: "Suggestion",
    requirement: "Exigence",
    bug: "Bug",
    other: "Autre",
    subject: "Sujet",
    message: "Message",
    privacy: "N’indiquez pas de mots de passe, jetons d’accès, données privées de bibliothèque ou autres informations sensibles.",
    submit: "Envoyer",
    success: "Merci. Votre commentaire a été envoyé.",
    rate: "Trop de demandes. Veuillez réessayer plus tard.",
    invalid: "Veuillez vérifier le formulaire et réessayer.",
    failed: "Le commentaire n’a pas pu être envoyé. Veuillez réessayer plus tard."
  },
  tr: {
    title: "Calibre To Go geri bildirimi",
    intro: "Bir gereksinim, özellik isteği, hata bildirimi veya başka bir geri bildirim gönderin.",
    type: "Tür",
    feature: "Özellik isteği",
    requirement: "Gereksinim",
    bug: "Hata",
    other: "Diğer",
    subject: "Konu",
    message: "Mesaj",
    privacy: "Parola, erişim belirteci, özel kitaplık verisi veya başka hassas bilgiler girmeyin.",
    submit: "Geri bildirim gönder",
    success: "Teşekkürler. Geri bildiriminiz gönderildi.",
    rate: "Çok fazla istek gönderildi. Lütfen daha sonra tekrar deneyin.",
    invalid: "Lütfen formu kontrol edip tekrar deneyin.",
    failed: "Geri bildirim gönderilemedi. Lütfen daha sonra tekrar deneyin."
  }
};

function languageFrom(value) {
  const tag = String(value || "en").toLowerCase().split("-")[0];
  return copy[tag] ? tag : "en";
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function page(text, siteKey, version) {
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>${escapeHtml(text.title)}</title>
<script src="https://challenges.cloudflare.com/turnstile/v0/api.js" async defer></script>
<style>
body{font-family:system-ui,sans-serif;max-width:720px;margin:0 auto;padding:24px;line-height:1.45}
label{display:block;font-weight:600;margin-top:18px}
input,select,textarea,button{font:inherit;width:100%;box-sizing:border-box;padding:12px;margin-top:6px}
textarea{min-height:180px;resize:vertical}
small{display:block;margin-top:14px}
button{margin-top:20px;cursor:pointer}
.cf-turnstile{margin-top:20px}
</style>
</head>
<body>
<h1>${escapeHtml(text.title)}</h1>
<p>${escapeHtml(text.intro)}</p>
<form method="post" action="/submit">
<label>${escapeHtml(text.type)}
<select name="type" required>
<option value="feature">${escapeHtml(text.feature)}</option>
<option value="requirement">${escapeHtml(text.requirement)}</option>
<option value="bug">${escapeHtml(text.bug)}</option>
<option value="other">${escapeHtml(text.other)}</option>
</select>
</label>
<label>${escapeHtml(text.subject)}
<input name="subject" minlength="5" maxlength="120" required>
</label>
<label>${escapeHtml(text.message)}
<textarea name="message" minlength="10" maxlength="4000" required></textarea>
</label>
<input type="hidden" name="language" value="${escapeHtml(text.language || "en")}">
<input type="hidden" name="app_version" value="${escapeHtml(version)}">
<input type="hidden" name="client_id" id="client-id">
<div class="cf-turnstile" data-sitekey="${escapeHtml(siteKey)}" data-action="feedback"></div>
<small>${escapeHtml(text.privacy)}</small>
<button type="submit">${escapeHtml(text.submit)}</button>
</form>
<script>
const key="calibre-to-go-feedback-client";
let id=localStorage.getItem(key);
if(!id){id=crypto.randomUUID();localStorage.setItem(key,id);}
document.getElementById("client-id").value=id;
</script>
</body>
</html>`;
}

function messagePage(message) {
  return `<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
<style>body{font-family:system-ui,sans-serif;max-width:640px;margin:48px auto;padding:24px}</style>
<p>${escapeHtml(message)}</p>`;
}

async function verifyTurnstile(env, token, remoteIp) {
  const response = await fetch("https://challenges.cloudflare.com/turnstile/v0/siteverify", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      secret: env.TURNSTILE_SECRET_KEY,
      response: token,
      remoteip: remoteIp
    })
  });

  if (!response.ok) return { success: false };
  return response.json();
}

async function createIssue(env, form) {
  const issueTitle = `[App feedback][${form.type}] ${form.subject}`;
  const body = [
    form.message,
    "",
    "---",
    `App version: ${form.appVersion || "unknown"}`,
    `Language: ${form.language}`,
    "Submitted through the protected Calibre To Go feedback form."
  ].join("\n");

  return fetch(`https://api.github.com/repos/${env.GITHUB_REPO}/issues`, {
    method: "POST",
    headers: {
      "accept": "application/vnd.github+json",
      "authorization": `Bearer ${env.GITHUB_TOKEN}`,
      "content-type": "application/json",
      "user-agent": "calibre-to-go-feedback-worker",
      "x-github-api-version": "2022-11-28"
    },
    body: JSON.stringify({
      title: issueTitle,
      body
    })
  });
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const lang = languageFrom(url.searchParams.get("lang"));
    const text = { ...copy[lang], language: lang };

    if (request.method === "GET") {
      return new Response(
        page(
          text,
          env.TURNSTILE_SITE_KEY,
          url.searchParams.get("version") || ""
        ),
        {
          headers: {
            "content-type": "text/html; charset=utf-8",
            "cache-control": "no-store",
            "content-security-policy":
              "default-src 'self'; script-src 'self' https://challenges.cloudflare.com 'unsafe-inline'; frame-src https://challenges.cloudflare.com; connect-src 'self' https://challenges.cloudflare.com; style-src 'unsafe-inline'"
          }
        }
      );
    }

    if (request.method !== "POST" || url.pathname !== "/submit") {
      return new Response("Method not allowed", { status: 405 });
    }

    const formData = await request.formData();
    const form = {
      type: String(formData.get("type") || ""),
      subject: String(formData.get("subject") || "").trim(),
      message: String(formData.get("message") || "").trim(),
      language: languageFrom(formData.get("language")),
      appVersion: String(formData.get("app_version") || "").slice(0, 40),
      clientId: String(formData.get("client_id") || ""),
      token: String(formData.get("cf-turnstile-response") || "")
    };
    const responseText = copy[form.language];

    if (
      !["feature", "requirement", "bug", "other"].includes(form.type) ||
      form.subject.length < 5 ||
      form.subject.length > 120 ||
      form.message.length < 10 ||
      form.message.length > 4000 ||
      !/^[0-9a-f-]{20,64}$/i.test(form.clientId) ||
      !form.token
    ) {
      return new Response(messagePage(responseText.invalid), {
        status: 400,
        headers: { "content-type": "text/html; charset=utf-8" }
      });
    }

    const limit = await env.FEEDBACK_RATE_LIMITER.limit({
      key: form.clientId
    });
    if (!limit.success) {
      return new Response(messagePage(responseText.rate), {
        status: 429,
        headers: { "content-type": "text/html; charset=utf-8" }
      });
    }

    const verification = await verifyTurnstile(
      env,
      form.token,
      request.headers.get("CF-Connecting-IP") || undefined
    );

    if (
      !verification.success ||
      verification.action !== "feedback" ||
      (
        env.EXPECTED_HOSTNAME &&
        verification.hostname !== env.EXPECTED_HOSTNAME
      )
    ) {
      return new Response(messagePage(responseText.invalid), {
        status: 400,
        headers: { "content-type": "text/html; charset=utf-8" }
      });
    }

    const github = await createIssue(env, form);
    if (!github.ok) {
      console.error("GitHub issue creation failed", github.status, await github.text());
      return new Response(messagePage(responseText.failed), {
        status: 502,
        headers: { "content-type": "text/html; charset=utf-8" }
      });
    }

    return new Response(messagePage(responseText.success), {
      status: 201,
      headers: { "content-type": "text/html; charset=utf-8" }
    });
  }
};
