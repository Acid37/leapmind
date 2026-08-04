import { showSuccess, showError } from './pptUi';

export async function requestMicrophonePermission() {
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    showSuccess('麦克风权限已获取，可以使用语音识别功能');
    stream.getTracks().forEach((track) => track.stop());
  } catch (error) {
    console.error('获取麦克风权限失败:', error);
    showError('无法获取麦克风权限，语音识别功能将不可用。请在浏览器设置中允许麦克风访问。');
  }
}


