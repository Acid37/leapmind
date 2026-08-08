// UI helpers for status/messages

export function showError(message) {
  const errorDiv = document.getElementById('errorMessage');
  const successDiv = document.getElementById('successMessage');
  if (!errorDiv || !successDiv) return;
  errorDiv.textContent = message;
  errorDiv.style.display = 'block';
  successDiv.style.display = 'none';
  setTimeout(() => { errorDiv.style.display = 'none'; }, 5000);
}

export function showSuccess(message) {
  const successDiv = document.getElementById('successMessage');
  const errorDiv = document.getElementById('errorMessage');
  if (!successDiv || !errorDiv) return;
  successDiv.textContent = message;
  successDiv.style.display = 'block';
  errorDiv.style.display = 'none';
  setTimeout(() => { successDiv.style.display = 'none'; }, 3000);
}

export function updateRecognitionStatus(status, isActive) {
  const recognitionStatus = document.getElementById('recognitionStatus');
  const recognitionIndicator = document.getElementById('recognitionIndicator');
  if (!recognitionStatus || !recognitionIndicator) return;
  recognitionStatus.textContent = status;
  recognitionIndicator.classList.toggle('active', !!isActive);
}

export function showAIResponse(content, isLoading) {
  const aiResponse = document.getElementById('aiResponse');
  const responseContent = document.getElementById('responseContent');
  const aiLoading = document.getElementById('aiLoading');
  if (!aiResponse || !responseContent || !aiLoading) return;
  responseContent.textContent = content;
  aiLoading.style.display = isLoading ? 'inline-block' : 'none';
  aiResponse.classList.add('show');
}

export function hideAIResponse() {
  const aiResponse = document.getElementById('aiResponse');
  if (!aiResponse) return;
  aiResponse.classList.remove('show');
}


