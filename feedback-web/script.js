(() => {
  "use strict";

  const form = document.querySelector("#feedback-form");
  const categoryInputs = [...document.querySelectorAll('input[name="category"]')];
  const formStatus = document.querySelector("#form-status");
  const feedbackPage = document.querySelector("#feedback-page");
  const problemReportPage = document.querySelector("#problem-report-page");
  const backToFeedback = document.querySelector("#back-to-feedback");
  const problemForm = document.querySelector("#problem-report-form");
  const problemSubmitButton = document.querySelector("#problem-submit-button");
  const problemEmail = document.querySelector("#problem-email");
  const problemEmailError = document.querySelector("#problem-email-error");
  const problemAttachment = document.querySelector("#problem-attachment");
  const problemFileName = document.querySelector("#problem-file-name");
  const problemRemoveFile = document.querySelector("#problem-remove-file");
  const problemTurnstile = document.querySelector("#problem-turnstile");
  const problemSubmissionStatus = document.querySelector("#problem-submission-status");
  const suggestionPage = document.querySelector("#suggestion-page");
  const backToSuggestion = document.querySelector("#back-to-suggestion");
  const suggestionForm = document.querySelector("#suggestion-form");
  const suggestionSubmitButton = document.querySelector("#suggestion-submit-button");
  const suggestionEmail = document.querySelector("#suggestion-email");
  const suggestionEmailError = document.querySelector("#suggestion-email-error");
  const suggestionTurnstile = document.querySelector("#suggestion-turnstile");
  const suggestionSubmissionStatus = document.querySelector("#suggestion-submission-status");
  const generalFeedbackPage = document.querySelector("#general-feedback-page");
  const backToGeneralFeedback = document.querySelector("#back-to-general-feedback");
  const generalFeedbackForm = document.querySelector("#general-feedback-form");
  const generalFeedbackSubmitButton = document.querySelector("#general-feedback-submit-button");
  const generalFeedbackEmail = document.querySelector("#general-feedback-email");
  const generalFeedbackEmailError = document.querySelector("#general-feedback-email-error");
  const generalFeedbackTurnstile = document.querySelector("#general-feedback-turnstile");
  const generalFeedbackSubmissionStatus = document.querySelector("#general-feedback-submission-status");

  if (
    !(form instanceof HTMLFormElement) ||
    !(formStatus instanceof HTMLElement) ||
    !(feedbackPage instanceof HTMLElement) ||
    !(problemReportPage instanceof HTMLElement) ||
    !(backToFeedback instanceof HTMLButtonElement) ||
    !(problemForm instanceof HTMLFormElement) ||
    !(problemSubmitButton instanceof HTMLButtonElement) ||
    !(problemEmail instanceof HTMLInputElement) ||
    !(problemEmailError instanceof HTMLElement) ||
    !(problemAttachment instanceof HTMLInputElement) ||
    !(problemFileName instanceof HTMLElement) ||
    !(problemRemoveFile instanceof HTMLButtonElement) ||
    !(problemTurnstile instanceof HTMLElement) ||
    !(problemSubmissionStatus instanceof HTMLElement) ||
    !(suggestionPage instanceof HTMLElement) ||
    !(backToSuggestion instanceof HTMLButtonElement) ||
    !(suggestionForm instanceof HTMLFormElement) ||
    !(suggestionSubmitButton instanceof HTMLButtonElement) ||
    !(suggestionEmail instanceof HTMLInputElement) ||
    !(suggestionEmailError instanceof HTMLElement) ||
    !(suggestionTurnstile instanceof HTMLElement) ||
    !(suggestionSubmissionStatus instanceof HTMLElement) ||
    !(generalFeedbackPage instanceof HTMLElement) ||
    !(backToGeneralFeedback instanceof HTMLButtonElement) ||
    !(generalFeedbackForm instanceof HTMLFormElement) ||
    !(generalFeedbackSubmitButton instanceof HTMLButtonElement) ||
    !(generalFeedbackEmail instanceof HTMLInputElement) ||
    !(generalFeedbackEmailError instanceof HTMLElement) ||
    !(generalFeedbackTurnstile instanceof HTMLElement) ||
    !(generalFeedbackSubmissionStatus instanceof HTMLElement)
  ) {
    return;
  }

  const categoryError = document.querySelector("#category-error");
  const backendState = {
    enabled: false,
    siteKey: "",
    configPromise: null,
  };

  const formStates = {
    problem: {
      form: problemForm,
      button: problemSubmitButton,
      turnstileSlot: problemTurnstile,
      status: problemSubmissionStatus,
      defaultLabel: problemSubmitButton.textContent || "Submit Report",
      widgetId: null,
      token: null,
      submissionId: null,
      pending: false,
    },
    improvement: {
      form: suggestionForm,
      button: suggestionSubmitButton,
      turnstileSlot: suggestionTurnstile,
      status: suggestionSubmissionStatus,
      defaultLabel: suggestionSubmitButton.textContent || "Submit Suggestion",
      widgetId: null,
      token: null,
      submissionId: null,
      pending: false,
    },
    general: {
      form: generalFeedbackForm,
      button: generalFeedbackSubmitButton,
      turnstileSlot: generalFeedbackTurnstile,
      status: generalFeedbackSubmissionStatus,
      defaultLabel: generalFeedbackSubmitButton.textContent || "Submit Feedback",
      widgetId: null,
      token: null,
      submissionId: null,
      pending: false,
    },
  };

  function selectedCategory() {
    return categoryInputs.find((input) => input.checked)?.value ?? "";
  }

  function validateCategory() {
    const message = selectedCategory() ? "" : "Choose a feedback category.";
    categoryInputs.forEach((input) => {
      input.setAttribute("aria-invalid", message ? "true" : "false");
    });
    if (categoryError instanceof HTMLElement) {
      categoryError.textContent = message;
    }
    return !message;
  }

  function clearAttachment() {
    problemAttachment.value = "";
    problemFileName.textContent = "";
    problemFileName.hidden = true;
    problemRemoveFile.hidden = true;
  }

  function validateProblemEmail() {
    const message = problemEmail.value.trim() && !problemEmail.validity.valid
      ? "Enter a valid email address."
      : "";
    problemEmail.setAttribute("aria-invalid", message ? "true" : "false");
    problemEmailError.textContent = message;
    problemEmailError.hidden = !message;
    return !message;
  }

  function clearProblemEmailError() {
    problemEmail.setAttribute("aria-invalid", "false");
    problemEmailError.textContent = "";
    problemEmailError.hidden = true;
  }

  function validateSuggestionEmail() {
    const message = suggestionEmail.value.trim() && !suggestionEmail.validity.valid
      ? "Enter a valid email address."
      : "";
    suggestionEmail.setAttribute("aria-invalid", message ? "true" : "false");
    suggestionEmailError.textContent = message;
    suggestionEmailError.hidden = !message;
    return !message;
  }

  function clearSuggestionEmailError() {
    suggestionEmail.setAttribute("aria-invalid", "false");
    suggestionEmailError.textContent = "";
    suggestionEmailError.hidden = true;
  }

  function validateGeneralFeedbackEmail() {
    const message = generalFeedbackEmail.value.trim() && !generalFeedbackEmail.validity.valid
      ? "Enter a valid email address."
      : "";
    generalFeedbackEmail.setAttribute("aria-invalid", message ? "true" : "false");
    generalFeedbackEmailError.textContent = message;
    generalFeedbackEmailError.hidden = !message;
    return !message;
  }

  function clearGeneralFeedbackEmailError() {
    generalFeedbackEmail.setAttribute("aria-invalid", "false");
    generalFeedbackEmailError.textContent = "";
    generalFeedbackEmailError.hidden = true;
  }

  function validateDescription(field) {
    const message = field.value.trim() ? "" : "Enter a description.";
    field.setCustomValidity(message);
    return !message;
  }

  function setSubmissionStatus(state, message, status = "") {
    state.status.textContent = message;
    state.status.hidden = !message;
    state.status.dataset.state = status;
  }

  function resetTurnstile(state, hide = true) {
    const api = globalThis.turnstile;
    if (state.widgetId !== null && api) {
      try {
        api.reset(state.widgetId);
      } catch {
        state.widgetId = null;
      }
    }
    state.token = null;
    state.button.disabled = true;
    state.turnstileSlot.hidden = hide;
  }

  function loadTurnstileScript() {
    if (globalThis.turnstile) {
      return Promise.resolve(globalThis.turnstile);
    }

    return new Promise((resolve, reject) => {
      const existingScript = document.querySelector("script[data-pointiq-turnstile]");
      if (existingScript) {
        existingScript.addEventListener("load", () => resolve(globalThis.turnstile), { once: true });
        existingScript.addEventListener("error", reject, { once: true });
        return;
      }

      const script = document.createElement("script");
      script.async = true;
      script.defer = true;
      script.dataset.pointiqTurnstile = "true";
      script.src = "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit";
      script.addEventListener("load", () => {
        if (globalThis.turnstile) {
          resolve(globalThis.turnstile);
        } else {
          reject(new Error("Turnstile did not load."));
        }
      }, { once: true });
      script.addEventListener("error", reject, { once: true });
      document.head.appendChild(script);
    });
  }

  async function initializeBackend() {
    try {
      const response = await fetch(new URL("./api/feedback-config", window.location.href), {
        headers: { Accept: "application/json" },
      });
      if (!response.ok) return false;

      const config = await response.json();
      if (config?.enabled !== true || typeof config.siteKey !== "string" || !config.siteKey) {
        return false;
      }

      await loadTurnstileScript();
      backendState.siteKey = config.siteKey;
      backendState.enabled = true;
      return true;
    } catch {
      return false;
    }
  }

  async function ensureTurnstile(state) {
    if (!(await backendState.configPromise) || state.widgetId !== null) {
      return false;
    }

    const api = globalThis.turnstile;
    if (!api) return false;

    state.turnstileSlot.hidden = false;
    try {
      state.widgetId = api.render(state.turnstileSlot, {
        sitekey: backendState.siteKey,
        theme: "dark",
        size: "flexible",
        callback: (token) => {
          state.token = token;
          state.button.disabled = false;
        },
        "expired-callback": () => {
          state.token = null;
          state.button.disabled = true;
        },
        "error-callback": () => {
          state.token = null;
          state.button.disabled = true;
          setSubmissionStatus(state, "Security verification is unavailable. Try again.", "error");
        },
      });
      return true;
    } catch {
      state.turnstileSlot.hidden = true;
      return false;
    }
  }

  function activateForm(state) {
    setSubmissionStatus(state, "");
    if (state.widgetId !== null && globalThis.turnstile) {
      state.turnstileSlot.hidden = false;
      state.token = null;
      state.button.disabled = true;
      globalThis.turnstile.reset(state.widgetId);
    } else {
      resetTurnstile(state);
      void ensureTurnstile(state);
    }
  }

  function showProblemReport() {
    feedbackPage.hidden = true;
    problemReportPage.hidden = false;
    activateForm(formStates.problem);
    backToFeedback.focus();
  }

  function showSuggestion() {
    feedbackPage.hidden = true;
    suggestionPage.hidden = false;
    activateForm(formStates.improvement);
    backToSuggestion.focus();
  }

  function showGeneralFeedback() {
    feedbackPage.hidden = true;
    generalFeedbackPage.hidden = false;
    activateForm(formStates.general);
    backToGeneralFeedback.focus();
  }

  function resetFeedbackPage() {
    problemForm.reset();
    problemForm.querySelector("#problem-description")?.setCustomValidity("");
    clearProblemEmailError();
    clearAttachment();
    suggestionForm.reset();
    suggestionForm.querySelector("#suggestion-description")?.setCustomValidity("");
    clearSuggestionEmailError();
    generalFeedbackForm.reset();
    generalFeedbackForm.querySelector("#general-feedback-description")?.setCustomValidity("");
    clearGeneralFeedbackEmailError();
    Object.values(formStates).forEach((state) => {
      state.pending = false;
      state.submissionId = null;
      state.button.textContent = state.defaultLabel;
      setSubmissionStatus(state, "");
      resetTurnstile(state);
    });
    problemReportPage.hidden = true;
    suggestionPage.hidden = true;
    generalFeedbackPage.hidden = true;
    feedbackPage.hidden = false;
    categoryInputs.forEach((input) => {
      input.checked = false;
      input.setAttribute("aria-invalid", "false");
    });
    if (categoryError instanceof HTMLElement) {
      categoryError.textContent = "";
    }
    categoryInputs[0]?.focus();
  }

  async function submitFeedback(event, state, category, categoryField, descriptionField, emailField, validateEmail, attachmentField = null) {
    event.preventDefault();
    if (state.pending || !validateDescription(descriptionField) || !validateEmail() || !state.form.reportValidity()) {
      return;
    }
    if (!backendState.enabled || !state.token) {
      return;
    }

    state.pending = true;
    state.button.disabled = true;
    state.button.textContent = "Submitting…";
    setSubmissionStatus(state, "");
    state.submissionId ||= crypto.randomUUID();

    const payload = new FormData();
    payload.append("category", category);
    payload.append("categoryValue", categoryField.value);
    payload.append("description", descriptionField.value.trim());
    payload.append("email", emailField.value.trim());
    payload.append("submissionId", state.submissionId);
    payload.append("turnstileToken", state.token);
    const file = attachmentField?.files?.[0];
    if (file) {
      payload.append("attachment", file, file.name);
    }

    try {
      const response = await fetch(new URL("./api/feedback", window.location.href), {
        method: "POST",
        headers: { Accept: "application/json" },
        body: payload,
      });
      let result = null;
      try {
        result = await response.json();
      } catch {
        result = null;
      }

      if (response.ok) {
        setSubmissionStatus(
          state,
          "Thanks — your feedback was received.",
          "success",
        );
        state.form.reset();
        descriptionField.setCustomValidity("");
        if (attachmentField) clearAttachment();
        validateEmail();
        state.submissionId = null;
        resetTurnstile(state);
        formStatus.textContent = "Thanks — your feedback was received.";
        resetFeedbackPage();
      } else if (result?.error?.code === "duplicate_submission") {
        setSubmissionStatus(state, "This submission was already received.", "success");
        state.form.reset();
        descriptionField.setCustomValidity("");
        if (attachmentField) clearAttachment();
        validateEmail();
        state.submissionId = null;
        resetTurnstile(state);
      } else {
        setSubmissionStatus(state, "We couldn’t submit this right now. Please try again.", "error");
        resetTurnstile(state, false);
      }
    } catch {
      setSubmissionStatus(state, "We couldn’t submit this right now. Please try again.", "error");
      resetTurnstile(state, false);
    } finally {
      state.pending = false;
      state.button.textContent = state.defaultLabel;
    }
  }

  categoryInputs.forEach((input) => {
    input.addEventListener("change", () => {
      validateCategory();
      if (input.checked && input.value === "problem") {
        showProblemReport();
      } else if (input.checked && input.value === "improvement") {
        showSuggestion();
      } else if (input.checked && input.value === "general") {
        showGeneralFeedback();
      }
    });
  });

  backToFeedback.addEventListener("click", resetFeedbackPage);
  backToSuggestion.addEventListener("click", resetFeedbackPage);
  backToGeneralFeedback.addEventListener("click", resetFeedbackPage);

  problemEmail.addEventListener("input", validateProblemEmail);
  problemEmail.addEventListener("blur", validateProblemEmail);
  suggestionEmail.addEventListener("input", validateSuggestionEmail);
  suggestionEmail.addEventListener("blur", validateSuggestionEmail);
  generalFeedbackEmail.addEventListener("input", validateGeneralFeedbackEmail);
  generalFeedbackEmail.addEventListener("blur", validateGeneralFeedbackEmail);

  [
    problemForm.querySelector("#problem-description"),
    suggestionForm.querySelector("#suggestion-description"),
    generalFeedbackForm.querySelector("#general-feedback-description"),
  ].forEach((field) => {
    field?.addEventListener("input", () => validateDescription(field));
  });

  problemAttachment.addEventListener("change", () => {
    const selectedFile = problemAttachment.files?.[0];
    if (!selectedFile) {
      clearAttachment();
      return;
    }

    problemFileName.textContent = selectedFile.name;
    problemFileName.hidden = false;
    problemRemoveFile.hidden = false;
  });

  problemRemoveFile.addEventListener("click", clearAttachment);

  problemForm.addEventListener("submit", (event) => {
    void submitFeedback(
      event,
      formStates.problem,
      "problem",
      problemForm.querySelector("#problem-type"),
      problemForm.querySelector("#problem-description"),
      problemEmail,
      validateProblemEmail,
      problemAttachment,
    );
  });

  suggestionForm.addEventListener("submit", (event) => {
    void submitFeedback(
      event,
      formStates.improvement,
      "improvement",
      suggestionForm.querySelector("#suggestion-type"),
      suggestionForm.querySelector("#suggestion-description"),
      suggestionEmail,
      validateSuggestionEmail,
    );
  });

  generalFeedbackForm.addEventListener("submit", (event) => {
    void submitFeedback(
      event,
      formStates.general,
      "general",
      generalFeedbackForm.querySelector('input[name="general-rating"]:checked') || generalFeedbackForm.querySelector('input[name="general-rating"]'),
      generalFeedbackForm.querySelector("#general-feedback-description"),
      generalFeedbackEmail,
      validateGeneralFeedbackEmail,
    );
  });

  Object.values(formStates).forEach((state) => {
    state.button.disabled = true;
  });
  backendState.configPromise = initializeBackend();
})();
