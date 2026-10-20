(() => {
  "use strict";

  const form = document.querySelector("#feedback-form");
  const categoryInputs = [...document.querySelectorAll('input[name="category"]')];
  const submitButton = document.querySelector("#submit-button");
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
  const suggestionPage = document.querySelector("#suggestion-page");
  const backToSuggestion = document.querySelector("#back-to-suggestion");
  const suggestionForm = document.querySelector("#suggestion-form");
  const suggestionSubmitButton = document.querySelector("#suggestion-submit-button");
  const suggestionEmail = document.querySelector("#suggestion-email");
  const suggestionEmailError = document.querySelector("#suggestion-email-error");
  const generalFeedbackPage = document.querySelector("#general-feedback-page");
  const backToGeneralFeedback = document.querySelector("#back-to-general-feedback");
  const generalFeedbackForm = document.querySelector("#general-feedback-form");
  const generalFeedbackSubmitButton = document.querySelector("#general-feedback-submit-button");
  const generalFeedbackEmail = document.querySelector("#general-feedback-email");
  const generalFeedbackEmailError = document.querySelector("#general-feedback-email-error");

  if (
    !(form instanceof HTMLFormElement) ||
    !(submitButton instanceof HTMLButtonElement) ||
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
    !(suggestionPage instanceof HTMLElement) ||
    !(backToSuggestion instanceof HTMLButtonElement) ||
    !(suggestionForm instanceof HTMLFormElement) ||
    !(suggestionSubmitButton instanceof HTMLButtonElement) ||
    !(suggestionEmail instanceof HTMLInputElement) ||
    !(suggestionEmailError instanceof HTMLElement) ||
    !(generalFeedbackPage instanceof HTMLElement) ||
    !(backToGeneralFeedback instanceof HTMLButtonElement) ||
    !(generalFeedbackForm instanceof HTMLFormElement) ||
    !(generalFeedbackSubmitButton instanceof HTMLButtonElement) ||
    !(generalFeedbackEmail instanceof HTMLInputElement) ||
    !(generalFeedbackEmailError instanceof HTMLElement)
  ) {
    return;
  }

  const categoryError = document.querySelector("#category-error");

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

  function validateForm() {
    return validateCategory();
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

  function showProblemReport() {
    feedbackPage.hidden = true;
    problemReportPage.hidden = false;
    backToFeedback.focus();
  }

  function showSuggestion() {
    feedbackPage.hidden = true;
    suggestionPage.hidden = false;
    backToSuggestion.focus();
  }

  function showGeneralFeedback() {
    feedbackPage.hidden = true;
    generalFeedbackPage.hidden = false;
    backToGeneralFeedback.focus();
  }

  function resetFeedbackPage() {
    problemForm.reset();
    clearProblemEmailError();
    clearAttachment();
    suggestionForm.reset();
    clearSuggestionEmailError();
    generalFeedbackForm.reset();
    clearGeneralFeedbackEmailError();
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
    event.preventDefault();
    validateProblemEmail();
  });

  suggestionForm.addEventListener("submit", (event) => {
    event.preventDefault();
    validateSuggestionEmail();
  });

  generalFeedbackForm.addEventListener("submit", (event) => {
    event.preventDefault();
    validateGeneralFeedbackEmail();
  });

  form.addEventListener("submit", (event) => {
    event.preventDefault();
    if (validateForm()) {
      formStatus.textContent =
        "Submission will become available after secure backend integration.";
    }
  });

  submitButton.disabled = true;
  problemSubmitButton.disabled = true;
  suggestionSubmitButton.disabled = true;
  generalFeedbackSubmitButton.disabled = true;
})();
