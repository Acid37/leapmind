// Audio split utilities

// Find delimiter-prefixed segments and split into Uint8Array chunks
export function splitAudioByDelimiter(arrayBuffer) {
  const delimiterPrefix = new Uint8Array([0xff, 0xfe, 0xfd, 0xfc]);
  const fullDelimiterLength = 8; // 4 bytes marker + 4 bytes length
  const audioData = new Uint8Array(arrayBuffer);
  const segments = [];
  const delimiterPositions = [];

  for (let i = 0; i <= audioData.length - fullDelimiterLength; i++) {
    let found = true;
    for (let j = 0; j < delimiterPrefix.length; j++) {
      if (audioData[i + j] !== delimiterPrefix[j]) {
        found = false;
        break;
      }
    }
    if (found) {
      delimiterPositions.push(i);
      i += fullDelimiterLength - 1;
    }
  }

  let startIndex = 0;
  for (let i = 0; i < delimiterPositions.length; i++) {
    const endIndex = delimiterPositions[i];
    if (endIndex > startIndex) {
      const segmentData = audioData.slice(startIndex, endIndex);
      if (segmentData.length > 0) segments.push(segmentData);
    }
    startIndex = delimiterPositions[i] + fullDelimiterLength;
  }
  if (startIndex < audioData.length) {
    const segmentData = audioData.slice(startIndex);
    if (segmentData.length > 0) segments.push(segmentData);
  }

  // filter tiny segments
  const minSegmentSize = 1024;
  return segments.filter((seg) => seg.length >= minSegmentSize);
}

function detectMimeType(audioData) {
  // Try to detect from magic numbers
  try {
    const view = audioData instanceof Uint8Array ? audioData : new Uint8Array(audioData);
    const readAscii = (start, len) => String.fromCharCode(...view.slice(start, start + len));
    const ascii4 = readAscii(0, 4);
    if (ascii4 === 'RIFF' && readAscii(8, 4) === 'WAVE') return 'audio/wav';
    if (ascii4 === 'OggS') return 'audio/ogg';
    if (ascii4 === 'fLaC') return 'audio/flac';
    if (ascii4 === 'ID3') return 'audio/mpeg';
    // MP3 frame sync: 0xffe (111111111110) → first 11 bits set
    if (view.length > 2 && view[0] === 0xff && (view[1] & 0xe0) === 0xe0) return 'audio/mpeg';
  } catch (_) { /* ignore */ }
  // Default to wav; most backends emit WAV for synthesized speech
  return 'audio/wav';
}

export function createAudioBlob(audioData) {
  const mime = detectMimeType(audioData);
  return new Blob([audioData], { type: mime });
}


