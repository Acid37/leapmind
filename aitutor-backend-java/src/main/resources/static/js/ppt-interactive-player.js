// 全局变量
let currentCourseId = '';
let currentPageNumber = 1;
let audioSegments = [];
let splitAudioBlobs = []; // 存储拆分后的音频片段
let currentSegmentIndex = 0;
let isPlaying = false;
let isPaused = false;
let speechRecognition = null;
let isRecognizing = false;
let mainAudio = null;
let isInterrupted = false;
let interruptedSegmentIndex = -1;

// 语音识别相关变量（参考ai-teacher.html）
let isVoiceDetectionActive = false;
let finalTranscript = '';
let interimTranscript = '';
let recognitionTimeout = null;
let networkErrorCount = 0;
const MAX_NETWORK_ERRORS = 3;
const RECOGNITION_TIMEOUT = 10000; // 10秒超时
const MIN_CONFIDENCE = 0.6; // 最小置信度阈值

// 音频播放位置记录
let interruptedPosition = 0; // 被打断时的播放位置（秒）
let enableVoiceDetectionFlag = true; // 是否启用语音检测
let resumeMode = 'fromBeginning'; // 恢复模式：'fromBeginning' 或 'fromPosition'

// 唤醒词设置
const WAKE_WORDS = ['小思老师', '老师', '小思']; // 唤醒词列表
let enableWakeWordDetection = true; // 是否启用唤醒词检测

// 初始化
document.addEventListener('DOMContentLoaded', function () {
    initializeAudio();
    initializeSpeechRecognition();
    setupEventListeners();
    requestMicrophonePermission();
});

// 请求麦克风权限
async function requestMicrophonePermission() {
    try {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        console.log('麦克风权限已获取');
        stream.getTracks().forEach(track => track.stop()); // 停止流，只是为了获取权限
        showSuccess('麦克风权限已获取，可以使用语音识别功能');
    } catch (error) {
        console.error('获取麦克风权限失败:', error);
        showError('无法获取麦克风权限，语音识别功能将不可用。请在浏览器设置中允许麦克风访问。');
    }
}

// 检测是否包含唤醒词
function containsWakeWord(transcript) {
    if (!enableWakeWordDetection) {
        return true; // 如果禁用唤醒词检测，则总是返回true
    }

    const text = transcript.toLowerCase().trim();

    // 检查是否包含任何唤醒词
    const hasWakeWord = WAKE_WORDS.some(wakeWord => {
        const lowerWakeWord = wakeWord.toLowerCase();
        return text.includes(lowerWakeWord);
    });

    console.log('唤醒词检测:', {
        text: text,
        wakeWords: WAKE_WORDS,
        hasWakeWord: hasWakeWord,
        enableWakeWordDetection: enableWakeWordDetection
    });

    return hasWakeWord;
}

// 提取问题内容（去除唤醒词）
function extractQuestionFromTranscript(transcript) {
    let text = transcript.trim();

    // 移除开头的唤醒词
    for (const wakeWord of WAKE_WORDS) {
        const patterns = [
            new RegExp(`^${wakeWord}[，,。.！!？?\\s]*`, 'i'), // 唤醒词在开头，后面跟标点或空格
            new RegExp(`^${wakeWord}`, 'i') // 简单的唤醒词匹配
        ];

        for (const pattern of patterns) {
            if (pattern.test(text)) {
                text = text.replace(pattern, '').trim();
                console.log(`移除唤醒词 "${wakeWord}"，剩余问题: "${text}"`);
                break;
            }
        }
    }

    return text;
}

// 检测是否为有效的提问
function isValidQuestion(transcript) {
    const text = transcript.toLowerCase().trim();

    // 提问关键词列表
    const questionKeywords = [
        '请问', '什么', '为什么', '怎么', '如何', '能否', '可以',
        '问题', '疑问', '不懂', '不明白', '解释', '说明', '帮助',
        '？', '?', '吗', '呢', '啊', '吧'
    ];

    // 提问句式模式
    const questionPatterns = [
        /^(请问)/,                // 以"请问"开头
        /[？?]$/,                 // 以问号结尾
        /(什么|为什么|怎么|如何|能否|可以)/,  // 包含疑问词
        /(不懂|不明白|不理解)/,    // 表示困惑
        /(解释|说明|讲解).*[吗呢啊吧]?$/  // 请求解释
    ];

    // 检查是否包含提问关键词
    const hasKeyword = questionKeywords.some(keyword => text.includes(keyword));

    // 检查是否匹配提问句式
    const matchesPattern = questionPatterns.some(pattern => pattern.test(text));

    // 长度检查：太短的可能是误识别
    const hasValidLength = text.length >= 2;

    console.log('提问检测:', {
        text: text,
        hasKeyword: hasKeyword,
        matchesPattern: matchesPattern,
        hasValidLength: hasValidLength,
        isValid: (hasKeyword || matchesPattern) && hasValidLength
    });

    return (hasKeyword || matchesPattern) && hasValidLength;
}

// 初始化音频播放器
function initializeAudio() {
    mainAudio = document.getElementById('mainAudio');
    const volumeSlider = document.getElementById('volumeSlider');

    mainAudio.volume = 0.8;

    mainAudio.addEventListener('ended', function () {
        onSegmentEnded();
    });

    mainAudio.addEventListener('error', function (e) {
        console.error('音频播放错误:', e);
        showError('音频播放失败，请检查音频文件');
    });

    volumeSlider.addEventListener('input', function () {
        mainAudio.volume = this.value / 100;
    });
}

// 初始化语音识别（参考ai-teacher.html实现）
function initializeSpeechRecognition() {
    if (!('webkitSpeechRecognition' in window) && !('SpeechRecognition' in window)) {
        showError('您的浏览器不支持语音识别功能，请使用Chrome、Edge或Safari浏览器');
        return false;
    }

    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    speechRecognition = new SpeechRecognition();

    // 配置语音识别参数（参考ai-teacher.html）
    speechRecognition.continuous = true; // 持续识别
    speechRecognition.interimResults = true; // 显示中间结果
    speechRecognition.lang = 'zh-CN';
    speechRecognition.maxAlternatives = 1;

    // 语音识别开始事件
    speechRecognition.onstart = function () {
        isRecognizing = true;
        updateRecognitionStatus('正在监听语音...', true);
        console.log('🎤 语音识别已启动');
    };

    // 语音识别结果事件（参考ai-teacher.html）
    speechRecognition.onresult = function (event) {
        finalTranscript = '';
        interimTranscript = '';

        // 处理识别结果
        for (let i = event.resultIndex; i < event.results.length; i++) {
            const transcript = event.results[i][0].transcript;
            const confidence = event.results[i][0].confidence;

            // 过滤空的识别结果
            if (!transcript || transcript.trim().length === 0) {
                console.log('🔇 跳过空的识别结果');
                continue;
            }

            console.log(`🎤 识别结果: "${transcript}", 置信度: ${(confidence * 100).toFixed(1)}%`);

            if (event.results[i].isFinal) {
                // 最终结果
                console.log(`🎯 最终识别结果检查: "${transcript}", 置信度: ${(confidence * 100).toFixed(1)}%, 阈值: ${(MIN_CONFIDENCE * 100).toFixed(1)}%`);

                if (confidence >= MIN_CONFIDENCE && transcript.trim().length > 0) {
                    finalTranscript += transcript;
                    console.log('✅ 置信度通过，最终识别结果:', finalTranscript);

                    // 处理识别到的语音
                    handleVoiceRecognitionResult(finalTranscript.trim());
                } else {
                    console.log(`❌ 识别置信度过低 (${(confidence * 100).toFixed(1)}%)，需要 ≥ ${(MIN_CONFIDENCE * 100).toFixed(1)}%，忽略结果: "${transcript}"`);
                    // 不更新状态，避免频繁显示错误信息
                }
            } else {
                // 中间结果
                if (transcript.trim().length > 0) {
                    interimTranscript += transcript;
                    updateRecognitionStatus(`正在识别: ${interimTranscript}`, true);

                    // 显示中间结果
                    const transcriptDisplay = document.getElementById('transcriptDisplay');
                    if (transcriptDisplay) {
                        transcriptDisplay.textContent = interimTranscript + '...';
                        transcriptDisplay.classList.add('has-content');
                        transcriptDisplay.style.borderColor = '#ffc107';
                    }
                }
            }
        }

        // 重置超时计时器
        resetRecognitionTimeout();
    };

    // 语音识别错误事件
    speechRecognition.onerror = function (event) {
        console.error('❌ 语音识别错误:', event.error);
        isRecognizing = false;

        let errorMessage = '语音识别错误: ';
        let shouldRetry = false;

        switch (event.error) {
            case 'network':
                networkErrorCount++;
                errorMessage += `网络连接问题 (${networkErrorCount}/${MAX_NETWORK_ERRORS})`;
                shouldRetry = networkErrorCount < MAX_NETWORK_ERRORS;
                break;
            case 'not-allowed':
                errorMessage += '麦克风权限被拒绝，请允许麦克风访问';
                break;
            case 'no-speech':
                // 静默处理无语音错误，这很常见
                console.log('🔇 未检测到语音输入，继续监听');
                shouldRetry = true;
                // 不显示错误消息，直接重试
                if (shouldRetry && isVoiceDetectionActive) {
                    const retryDelay = 500; // 短延迟重试
                    setTimeout(() => {
                        if (isVoiceDetectionActive && !isRecognizing) {
                            restartSpeechRecognition();
                        }
                    }, retryDelay);
                }
                return; // 跳过错误显示
            case 'aborted':
                // 静默处理中止错误
                console.log('🔇 语音识别被中止');
                return;
            case 'audio-capture':
                errorMessage += '音频捕获失败，请检查麦克风';
                break;
            case 'service-not-allowed':
                errorMessage += '语音识别服务不可用';
                shouldRetry = true;
                break;
            default:
                errorMessage += event.error;
                shouldRetry = true;
        }

        updateRecognitionStatus(errorMessage, false);

        // 自动重试机制
        if (shouldRetry && isVoiceDetectionActive) {
            const retryDelay = Math.min(1000 * Math.pow(2, networkErrorCount), 10000);
            console.log(`🔄 ${retryDelay}ms后重试语音识别...`);
            setTimeout(() => {
                if (isVoiceDetectionActive && !isRecognizing) {
                    restartSpeechRecognition();
                }
            }, retryDelay);
        }
    };

    // 语音识别结束事件
    speechRecognition.onend = function () {
        console.log('🎤 语音识别已结束');
        isRecognizing = false;
        updateRecognitionStatus('语音识别已停止', false);

        // 自动重启机制 - 只有在需要时才重启
        if (isVoiceDetectionActive && isPlaying && !isInterrupted) {
            console.log('🔄 准备重启语音识别...');
            setTimeout(() => {
                if (isVoiceDetectionActive && isPlaying && !isInterrupted && !isRecognizing) {
                    console.log('🔄 重启语音识别');
                    restartSpeechRecognition();
                } else {
                    console.log('🔇 跳过重启语音识别 - 条件不满足');
                }
            }, 500); // 增加延迟，避免频繁重启
        } else {
            console.log('🔇 不需要重启语音识别');
        }
    };

    return true;
}

// 设置事件监听器
function setupEventListeners() {
    // 键盘快捷键
    document.addEventListener('keydown', function (e) {
        if (e.code === 'Space' && e.target.tagName !== 'INPUT') {
            e.preventDefault();
            if (isPlaying) {
                pausePlayback();
            } else {
                startPlayback();
            }
        }
    });
}

// 音频拆分函数
function splitAudioByDelimiter(audioBuffer) {
    // 只匹配前4个字节的分隔符标识，后4个字节是长度信息（动态变化）
    const delimiterPrefix = new Uint8Array([0xFF, 0xFE, 0xFD, 0xFC]);
    const fullDelimiterLength = 8; // 完整分隔符长度：4字节标识 + 4字节长度
    const audioData = new Uint8Array(audioBuffer);
    const segments = [];
    let startIndex = 0;
    let delimiterPositions = [];

    console.log('🔍 开始拆分音频，总长度:', audioData.length);
    console.log('🔍 分隔符前缀:', Array.from(delimiterPrefix).map(b => '0x' + b.toString(16).toUpperCase()).join(' '));

    // 首先找到所有分隔符位置（只匹配前4个字节）
    for (let i = 0; i <= audioData.length - fullDelimiterLength; i++) {
        let found = true;
        // 只检查前4个字节的分隔符标识
        for (let j = 0; j < delimiterPrefix.length; j++) {
            if (audioData[i + j] !== delimiterPrefix[j]) {
                found = false;
                break;
            }
        }

        if (found) {
            delimiterPositions.push(i);
            // 读取后4个字节的长度信息（用于调试）
            const lengthBytes = audioData.slice(i + 4, i + 8);
            const audioLength = (lengthBytes[0] << 24) | (lengthBytes[1] << 16) | (lengthBytes[2] << 8) | lengthBytes[3];
            console.log(`🔍 找到分隔符位置: ${i}, 前一片段长度: ${audioLength} bytes`);
            i += fullDelimiterLength - 1; // 跳过完整的8字节分隔符
        }
    }

    console.log(`🔍 共找到 ${delimiterPositions.length} 个分隔符`);

    // 根据分隔符位置提取片段
    for (let i = 0; i < delimiterPositions.length; i++) {
        const endIndex = delimiterPositions[i];

        if (endIndex > startIndex) {
            const segmentData = audioData.slice(startIndex, endIndex);
            if (segmentData.length > 0) {
                segments.push(segmentData);
                console.log(`📄 提取音频片段 ${segments.length}，位置: ${startIndex}-${endIndex}，长度: ${segmentData.length}`);
            }
        }

        startIndex = delimiterPositions[i] + fullDelimiterLength; // 跳过完整的8字节分隔符
    }

    // 处理最后一个片段
    if (startIndex < audioData.length) {
        const segmentData = audioData.slice(startIndex);
        if (segmentData.length > 0) {
            segments.push(segmentData);
            console.log(`📄 提取最后音频片段 ${segments.length}，位置: ${startIndex}-${audioData.length}，长度: ${segmentData.length}`);
        }
    }

    // 过滤掉过小的片段（可能是噪音）
    const minSegmentSize = 1024; // 1KB
    const validSegments = segments.filter(segment => segment.length >= minSegmentSize);

    if (validSegments.length !== segments.length) {
        console.log(`🧹 过滤掉 ${segments.length - validSegments.length} 个过小的片段`);
    }

    console.log(`✅ 音频拆分完成，共 ${validSegments.length} 个有效片段`);
    return validSegments;
}

// 将Uint8Array转换为Blob
function createAudioBlob(audioData) {
    return new Blob([audioData], { type: 'audio/wav' });
}

// 加载页面音频数据
async function loadPageAudio() {
    const courseId = document.getElementById('courseId').value.trim();
    const pageNumber = document.getElementById('pageNumber').value.trim();

    if (!courseId || !pageNumber) {
        showError('请输入会话ID和页码');
        return;
    }

    try {
        showSuccess('正在加载页面音频数据...');

        // 1. 获取音频片段信息
        const segmentsResponse = await fetch(`http://localhost:8080/api/speech/ppt/${courseId}/page/${pageNumber}`);
        if (!segmentsResponse.ok) {
            throw new Error(`获取片段信息失败: ${segmentsResponse.status}`);
        }

        const segments = await segmentsResponse.json();
        if (!segments || segments.length === 0) {
            showError('未找到该页面的音频数据');
            return;
        }

        showSuccess('正在下载并拆分页面音频...');

        // 2. 获取合并的页面音频
        const audioResponse = await fetch(`http://localhost:8080/api/speech/ppt/${courseId}/page/${pageNumber}/audio`);
        if (!audioResponse.ok) {
            throw new Error(`获取页面音频失败: ${audioResponse.status}`);
        }

        const audioBuffer = await audioResponse.arrayBuffer();
        console.log('📥 下载页面音频完成，大小:', audioBuffer.byteLength);

        // 3. 拆分音频
        const splitAudioData = splitAudioByDelimiter(audioBuffer);

        if (splitAudioData.length === 0) {
            throw new Error('音频拆分失败，未找到有效的音频片段');
        }

        if (splitAudioData.length !== segments.length) {
            console.warn(`⚠️ 拆分片段数量 (${splitAudioData.length}) 与预期 (${segments.length}) 不匹配`);
            showError(`警告：拆分得到 ${splitAudioData.length} 个片段，但预期 ${segments.length} 个`);
        }

        // 4. 创建音频Blob
        splitAudioBlobs = splitAudioData.map((data, index) => {
            const blob = createAudioBlob(data);
            console.log(`🎵 创建音频片段 ${index + 1} Blob，大小: ${blob.size}`);
            return blob;
        });

        // 验证音频片段
        const validBlobs = splitAudioBlobs.filter(blob => blob.size > 0);
        if (validBlobs.length !== splitAudioBlobs.length) {
            console.warn(`⚠️ 发现 ${splitAudioBlobs.length - validBlobs.length} 个空音频片段`);
        }

        // 5. 设置全局变量
        currentCourseId = courseId;
        currentPageNumber = parseInt(pageNumber);
        audioSegments = segments;
        currentSegmentIndex = 0;

        displaySegments();
        updatePlaybackStatus();
        enablePlaybackControls();

        // 自动验证播放序列完整性
        const isValid = validatePlaybackSequence();

        if (isValid) {
            showSuccess(`成功加载并拆分 ${segments.length} 个音频片段`);
        } else {
            showError(`加载了 ${segments.length} 个片段，但存在完整性问题`);
        }

    } catch (error) {
        console.error('加载音频数据失败:', error);
        showError('加载音频数据失败: ' + error.message);
    }
}

// 显示音频片段列表
function displaySegments() {
    const segmentItems = document.getElementById('segmentItems');
    segmentItems.innerHTML = '';

    audioSegments.forEach((segment, index) => {
        const segmentDiv = document.createElement('div');
        segmentDiv.className = 'segment-item';
        segmentDiv.onclick = () => playSegment(index);

        // 显示音频片段大小信息
        const audioSize = splitAudioBlobs[index] ?
            `${(splitAudioBlobs[index].size / 1024).toFixed(1)}KB` : '未加载';

        segmentDiv.innerHTML = `
        <div class="segment-info">
            <div class="segment-title">片段 ${index + 1} (${audioSize})</div>
            <div class="segment-text">${segment.textContent || '无文本内容'}</div>
        </div>
        <div class="segment-duration">${formatDuration(segment.duration || 0)}</div>
    `;

        segmentItems.appendChild(segmentDiv);
    });

    updateSegmentDisplay();
}

// 更新片段显示状态
function updateSegmentDisplay() {
    const segmentItems = document.querySelectorAll('#segmentItems .segment-item');
    segmentItems.forEach((item, index) => {
        item.classList.remove('current', 'played');
        if (index === currentSegmentIndex) {
            item.classList.add('current');
        } else if (index < currentSegmentIndex) {
            item.classList.add('played');
        }
    });
}

// 开始播放
function startPlayback() {
    if (audioSegments.length === 0) {
        showError('请先加载音频数据');
        return;
    }

    // 重置所有状态
    isPlaying = true;
    isPaused = false;
    isInterrupted = false;
    onSegmentEnded.isProcessing = false;

    console.log(`🎬 [startPlayback] 开始播放，从片段 ${currentSegmentIndex + 1} 开始`);

    playCurrentSegment();
    startVoiceDetection(); // 使用新的语音检测函数
    updatePlaybackControls();
    updatePlaybackStatus();
}

// 暂停播放
function pausePlayback() {
    isPaused = true;
    isPlaying = false;
    mainAudio.pause();
    stopVoiceDetection(); // 使用新的语音检测函数
    updatePlaybackControls();
    updatePlaybackStatus();
}

// 停止播放
function stopPlayback() {
    isPlaying = false;
    isPaused = false;
    isInterrupted = false;
    currentSegmentIndex = 0;

    // 重置 onSegmentEnded 的处理标志
    onSegmentEnded.isProcessing = false;

    mainAudio.pause();
    mainAudio.currentTime = 0;

    // 清除事件监听器
    mainAudio.onended = null;
    mainAudio.onerror = null;

    stopVoiceDetection(); // 使用新的语音检测函数
    updatePlaybackControls();
    updatePlaybackStatus();
    updateSegmentDisplay();
}

// 播放指定片段
function playSegment(index) {
    if (index >= 0 && index < audioSegments.length && index < splitAudioBlobs.length) {
        console.log(`🎯 跳转到片段 ${index + 1}`);
        currentSegmentIndex = index;

        if (isPlaying) {
            // 如果正在播放，立即切换到指定片段
            playCurrentSegment();
        }

        updateSegmentDisplay();
        updatePlaybackStatus();
    } else {
        console.error('片段索引无效:', index, '/', audioSegments.length, '/', splitAudioBlobs.length);
        showError('无法播放指定片段，索引无效');
    }
}

// 播放当前片段
async function playCurrentSegment() {
    console.log(`🎯 [playCurrentSegment] 尝试播放片段 ${currentSegmentIndex + 1}/${audioSegments.length}`);

    if (currentSegmentIndex >= audioSegments.length) {
        // 播放完成
        console.log('🎉 [playCurrentSegment] 所有片段播放完成');
        stopPlayback();
        showSuccess('所有音频片段播放完成！');
        return;
    }

    if (splitAudioBlobs.length === 0) {
        console.error('❌ [playCurrentSegment] 音频片段未加载');
        showError('音频片段未加载，请先加载页面音频');
        return;
    }

    if (currentSegmentIndex >= splitAudioBlobs.length) {
        console.error(`❌ [playCurrentSegment] 片段索引超出范围: ${currentSegmentIndex + 1} > ${splitAudioBlobs.length}`);
        showError('音频片段索引错误');
        return;
    }

    const segment = audioSegments[currentSegmentIndex];
    const audioBlob = splitAudioBlobs[currentSegmentIndex];

    // 详细的片段信息日志
    console.log(`📊 [playCurrentSegment] 片段 ${currentSegmentIndex + 1} 详情:`);
    console.log(`   📝 文本内容: "${segment?.textContent || '无文本'}"`);
    console.log(`   🎵 音频大小: ${audioBlob ? (audioBlob.size / 1024).toFixed(1) + 'KB' : '无音频'}`);
    console.log(`   ⏱️ 预期时长: ${segment?.duration ? (segment.duration / 1000).toFixed(1) + 's' : '未知'}`);

    if (!audioBlob || audioBlob.size === 0) {
        console.warn(`⚠️ [playCurrentSegment] 片段 ${currentSegmentIndex + 1} 音频为空，跳过`);
        // 跳过空片段，直接播放下一个
        onSegmentEnded();
        return;
    }

    try {
        // 先暂停当前播放，避免冲突
        if (!mainAudio.paused) {
            mainAudio.pause();
            await new Promise(resolve => setTimeout(resolve, 50)); // 短暂等待
        }

        // 使用拆分后的音频片段
        const audioUrl = URL.createObjectURL(audioBlob);

        // 清除之前的事件监听器
        mainAudio.onended = null;
        mainAudio.onerror = null;

        // 设置新的音频源
        mainAudio.src = audioUrl;

        // 等待音频加载
        await new Promise((resolve, reject) => {
            const loadTimeout = setTimeout(() => {
                reject(new Error('音频加载超时'));
            }, 5000);

            mainAudio.onloadeddata = () => {
                clearTimeout(loadTimeout);
                resolve();
            };

            mainAudio.onerror = (e) => {
                clearTimeout(loadTimeout);
                reject(new Error('音频加载失败'));
            };
        });

        // 设置事件监听器
        mainAudio.onended = function () {
            console.log(`🔊 [onended] 音频片段 ${currentSegmentIndex + 1} 播放结束`);
            onSegmentEnded();
        };

        mainAudio.onerror = function (e) {
            console.error(`❌ [onerror] 音频片段 ${currentSegmentIndex + 1} 播放错误:`, e);
            // 跳过错误片段，播放下一个
            setTimeout(() => {
                if (isPlaying && !isInterrupted) {
                    onSegmentEnded();
                }
            }, 500);
        };

        // 开始播放
        await mainAudio.play();
        console.log(`🎵 [playCurrentSegment] 成功开始播放片段 ${currentSegmentIndex + 1}`);

        updateSegmentDisplay();
        updatePlaybackStatus();

    } catch (error) {
        console.error(`❌ [playCurrentSegment] 播放片段 ${currentSegmentIndex + 1} 失败:`, error);

        // 检查错误类型
        if (error.name === 'AbortError') {
            console.log(`⚠️ [playCurrentSegment] 片段 ${currentSegmentIndex + 1} 播放被中断，可能是快速切换导致`);
            // AbortError 通常是因为快速切换导致的，不需要特殊处理
            return;
        } else if (error.name === 'NotSupportedError') {
            console.log(`⚠️ [playCurrentSegment] 片段 ${currentSegmentIndex + 1} 音频格式不支持，跳过`);
        } else {
            console.log(`⚠️ [playCurrentSegment] 片段 ${currentSegmentIndex + 1} 其他播放错误: ${error.name}`);
        }

        showError(`播放音频片段 ${currentSegmentIndex + 1} 失败: ${error.message}`);

        // 只有在非AbortError的情况下才跳过片段
        if (error.name !== 'AbortError') {
            console.log(`🔄 [playCurrentSegment] 尝试跳过失败的片段 ${currentSegmentIndex + 1}`);
            setTimeout(() => {
                if (isPlaying && !isInterrupted) {
                    onSegmentEnded();
                }
            }, 1000); // 增加延迟，避免快速切换
        }
    }
}

// 音频片段播放结束
function onSegmentEnded() {
    console.log(`🔊 [onSegmentEnded] 音频片段 ${currentSegmentIndex + 1} 播放结束`);

    // 防止重复调用
    if (onSegmentEnded.isProcessing) {
        console.log('⚠️ [onSegmentEnded] 正在处理中，跳过重复调用');
        return;
    }
    onSegmentEnded.isProcessing = true;

    if (!isPlaying || isInterrupted) {
        console.log('🔇 [onSegmentEnded] 播放已停止或被打断，不继续播放下一片段');
        onSegmentEnded.isProcessing = false;
        return;
    }

    // 记录当前播放完成的片段信息
    const completedSegment = audioSegments[currentSegmentIndex];
    console.log(`✅ 完成播放片段 ${currentSegmentIndex + 1}: "${completedSegment?.textContent?.substring(0, 30) || '无文本'}..."`);

    // 移动到下一个片段
    const previousIndex = currentSegmentIndex;
    currentSegmentIndex++;

    console.log(`📍 片段索引更新: ${previousIndex + 1} -> ${currentSegmentIndex + 1}`);

    if (currentSegmentIndex >= audioSegments.length) {
        // 所有片段播放完成
        console.log('🎉 所有音频片段播放完成');
        stopPlayback();
        showSuccess('所有音频片段播放完成！');
        return;
    }

    if (currentSegmentIndex >= splitAudioBlobs.length) {
        console.error(`❌ 下一个片段索引超出音频Blob范围: ${currentSegmentIndex + 1} > ${splitAudioBlobs.length}`);
        stopPlayback();
        showError('音频片段索引超出范围，播放停止');
        return;
    }

    // 检查下一个片段的有效性
    const nextSegment = audioSegments[currentSegmentIndex];
    const nextAudioBlob = splitAudioBlobs[currentSegmentIndex];

    console.log(`🔄 准备播放下一个片段 ${currentSegmentIndex + 1}/${audioSegments.length}:`);
    console.log(`   📝 文本: "${nextSegment?.textContent?.substring(0, 50) || '无文本'}..."`);
    console.log(`   🎵 音频大小: ${nextAudioBlob ? (nextAudioBlob.size / 1024).toFixed(1) + 'KB' : '无音频'}`);

    if (!nextAudioBlob || nextAudioBlob.size === 0) {
        console.warn(`⚠️ [onSegmentEnded] 片段 ${currentSegmentIndex + 1} 的音频为空，跳过到下一片段`);
        // 递归调用，跳过空片段，但要重置处理标志
        onSegmentEnded.isProcessing = false;
        setTimeout(() => {
            onSegmentEnded();
        }, 100);
        return;
    }

    // 播放下一个片段
    setTimeout(() => {
        if (isPlaying && !isInterrupted) {
            console.log(`🎵 [onSegmentEnded] 开始播放片段 ${currentSegmentIndex + 1}`);
            playCurrentSegment();
        } else {
            console.log('🔇 [onSegmentEnded] 播放状态已改变，取消播放下一片段');
        }
        onSegmentEnded.isProcessing = false; // 重置处理标志
    }, 200); // 增加延迟，确保状态更新和避免快速切换
}

// 启动语音检测（参考ai-teacher.html）
function startVoiceDetection() {
    // 检查基本条件
    if (!enableVoiceDetectionFlag) {
        console.log('🔇 语音检测已禁用，跳过启动');
        return;
    }

    // 检查播放状态
    if (!isPlaying || isInterrupted) {
        console.log('🔇 播放状态不合适，跳过启动语音检测:', {
            isPlaying,
            isInterrupted
        });
        return;
    }

    if (!speechRecognition && !initializeSpeechRecognition()) {
        console.log('🔇 语音识别初始化失败');
        return;
    }

    if (isRecognizing || isVoiceDetectionActive) {
        console.log('🎤 语音识别已在运行中，跳过启动:', {
            isRecognizing,
            isVoiceDetectionActive
        });
        return;
    }

    try {
        isVoiceDetectionActive = true;
        networkErrorCount = 0;
        finalTranscript = '';
        interimTranscript = '';

        speechRecognition.start();
        console.log('🎤 启动语音检测');
        updateRecognitionStatus('等待语音输入...', true);
    } catch (error) {
        console.error('启动语音检测失败:', error);
        isVoiceDetectionActive = false;
        updateRecognitionStatus('启动语音检测失败: ' + error.message, false);
    }
}

// 停止语音检测
function stopVoiceDetection() {
    console.log('🔇 停止语音检测');

    // 停止语音识别
    if (speechRecognition && isRecognizing) {
        try {
            speechRecognition.stop();
        } catch (error) {
            console.error('停止语音识别失败:', error);
        }
    }

    // 清理状态
    isVoiceDetectionActive = false;
    isRecognizing = false;
    finalTranscript = '';
    interimTranscript = '';

    // 清理计时器
    clearRecognitionTimeout();

    // 更新UI状态
    updateRecognitionStatus('语音检测已停止', false);

    // 清空识别显示
    const transcriptDisplay = document.getElementById('transcriptDisplay');
    if (transcriptDisplay) {
        transcriptDisplay.textContent = '等待语音输入...';
        transcriptDisplay.classList.remove('has-content');
        transcriptDisplay.style.borderColor = '#dee2e6';
    }
}

// 重启语音识别
function restartSpeechRecognition() {
    // 严格的条件检查
    if (!isVoiceDetectionActive || !speechRecognition || isRecognizing) {
        console.log('🔇 跳过重启语音识别 - 条件不满足:', {
            isVoiceDetectionActive,
            hasSpeechRecognition: !!speechRecognition,
            isRecognizing
        });
        return;
    }

    // 只有在播放且未被打断时才重启
    if (!isPlaying || isInterrupted) {
        console.log('🔇 跳过重启语音识别 - 播放状态不合适:', {
            isPlaying,
            isInterrupted
        });
        return;
    }

    try {
        speechRecognition.start();
        console.log('🔄 重启语音识别');
    } catch (error) {
        console.error('重启语音识别失败:', error);
        // 减少重试频率，避免无限循环
        setTimeout(() => {
            if (isVoiceDetectionActive && isPlaying && !isInterrupted) {
                restartSpeechRecognition();
            }
        }, 2000); // 增加延迟到2秒
    }
}

// 重置识别超时计时器
function resetRecognitionTimeout() {
    clearRecognitionTimeout();
    recognitionTimeout = setTimeout(() => {
        if (isRecognizing && !interimTranscript && !finalTranscript) {
            console.log('⏰ 识别超时，重新启动');
            speechRecognition.stop();
        }
    }, RECOGNITION_TIMEOUT);
}

// 清除识别超时计时器
function clearRecognitionTimeout() {
    if (recognitionTimeout) {
        clearTimeout(recognitionTimeout);
        recognitionTimeout = null;
    }
}

// 处理语音识别结果
function handleVoiceRecognitionResult(recognizedText) {
    console.log('🗣️ 处理语音识别结果:', recognizedText);

    // 严格验证识别结果
    if (!recognizedText || typeof recognizedText !== 'string' || recognizedText.trim().length < 2) {
        console.log('❌ 识别结果无效，忽略:', {
            text: recognizedText,
            type: typeof recognizedText,
            length: recognizedText ? recognizedText.trim().length : 0
        });
        return;
    }

    const cleanText = recognizedText.trim();

    // 过滤明显的误识别（如单个字符、数字等）
    if (cleanText.length < 2 || /^[0-9\s\.,!?]+$/.test(cleanText)) {
        console.log('❌ 识别结果疑似误识别，忽略:', cleanText);
        return;
    }

    console.log('✅ 识别结果有效:', cleanText);

    // 显示识别结果
    const transcriptDisplay = document.getElementById('transcriptDisplay');
    if (transcriptDisplay) {
        transcriptDisplay.textContent = cleanText;
        transcriptDisplay.classList.add('has-content');
        transcriptDisplay.style.borderColor = '#28a745';
    }

    // 如果正在播放且未被打断，立即打断
    if (isPlaying && !isInterrupted) {
        console.log('🎤 触发语音打断');
        handleVoiceInterruption(cleanText);
    } else {
        console.log('🔇 不触发打断:', {
            isPlaying,
            isInterrupted
        });
    }
}

// 处理语音打断
async function handleVoiceInterruption(question) {
    console.log('🎤 检测到语音，打断讲课:', question);

    // 暂停当前播放并记录位置
    if (mainAudio && !mainAudio.paused) {
        isInterrupted = true;
        interruptedSegmentIndex = currentSegmentIndex;
        interruptedPosition = mainAudio.currentTime; // 记录打断时的播放位置
        mainAudio.pause();
        console.log(`⏸️ 暂停讲课，片段 ${interruptedSegmentIndex + 1}，位置 ${interruptedPosition.toFixed(2)}s`);
    } else {
        isInterrupted = true;
        interruptedSegmentIndex = currentSegmentIndex;
        interruptedPosition = 0;
    }

    // 注意：不要设置 isPlaying = false，因为我们只是暂时打断，不是停止播放
    console.log('🎤 打断状态设置:', {
        isInterrupted,
        isPlaying,
        interruptedSegmentIndex,
        interruptedPosition
    });
    logCurrentState('handleVoiceInterruption-after-pause');

    // 停止语音检测
    stopVoiceDetection();

    // 更新UI状态
    updateInteractionStatus();
    showAIResponse('正在处理您的问题...', true);

    try {
        // 发送问题到后端
        const response = await fetch('http://localhost:8080/api/voice-chat/ask', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                courseId: currentCourseId,
                question: question
            })
        });

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }

        const result = await response.json();

        // 显示AI回答
        showAIResponse(result.answer, false);

        // 获取语音合成
        const ttsResponse = await fetch('http://localhost:8080/api/voice-chat/synthesize', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                courseId: currentCourseId,
                text: result.answer
            })
        });

        if (ttsResponse.ok) {
            const audioBlob = await ttsResponse.blob();
            const audioUrl = URL.createObjectURL(audioBlob);

            const responseAudio = document.getElementById('responseAudio');
            responseAudio.src = audioUrl;

            // 播放AI回答
            responseAudio.play();

            // 监听AI回答播放完成
            responseAudio.onended = function () {
                // 回答播放完成，恢复原来的播放
                resumePlayback();
            };
        } else {
            // TTS失败，直接恢复播放
            setTimeout(() => {
                resumePlayback();
            }, 3000);
        }

    } catch (error) {
        console.error('处理语音打断失败:', error);
        showError('处理问题失败: ' + error.message);

        // 错误情况下也要恢复播放
        setTimeout(() => {
            resumePlayback();
        }, 2000);
    }
}

// 停止语音识别
function stopSpeechRecognition() {
    if (speechRecognition && isRecognizing) {
        try {
            console.log('停止语音识别');
            speechRecognition.stop();
        } catch (error) {
            console.error('停止语音识别失败:', error);
        }
    }
}

// 收集完整的语音输入
function collectCompleteTranscript() {
    console.log('🎤 开始收集完整语音输入...');

    // 创建新的语音识别实例来收集完整输入
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    const completeRecognition = new SpeechRecognition();

    completeRecognition.continuous = false;
    completeRecognition.interimResults = true;
    completeRecognition.lang = 'zh-CN';
    completeRecognition.maxAlternatives = 1;

    let completeTranscript = '';
    let silenceTimer = null;

    completeRecognition.onstart = function () {
        console.log('🎤 完整语音收集已启动');
        updateRecognitionStatus('正在收集完整语音输入...', true);
    };

    completeRecognition.onresult = function (event) {
        let finalText = '';
        let interimText = '';

        for (let i = event.resultIndex; i < event.results.length; i++) {
            const transcript = event.results[i][0].transcript;
            if (event.results[i].isFinal) {
                finalText += transcript;
            } else {
                interimText += transcript;
            }
        }

        const currentText = finalText || interimText;
        const transcriptDisplay = document.getElementById('transcriptDisplay');
        transcriptDisplay.textContent = '🎤 ' + currentText + (interimText ? '...' : '');

        if (finalText) {
            completeTranscript = finalText;
            console.log('🎤 收集到最终语音:', completeTranscript);
        }

        // 重置静音计时器
        if (silenceTimer) {
            clearTimeout(silenceTimer);
        }

        // 设置静音检测（2秒无声音就结束收集）
        silenceTimer = setTimeout(() => {
            console.log('🎤 检测到静音，结束语音收集');
            completeRecognition.stop();
        }, 2000);
    };

    completeRecognition.onend = function () {
        console.log('🎤 完整语音收集结束，最终结果:', completeTranscript);

        if (silenceTimer) {
            clearTimeout(silenceTimer);
        }

        // 处理收集到的完整语音
        if (completeTranscript && completeTranscript.trim().length > 0) {
            handleSpeechInterruption(completeTranscript);
        } else {
            // 如果没有收集到有效语音，恢复播放
            console.log('🎤 未收集到有效语音，恢复播放');
            resumePlayback();
        }
    };

    completeRecognition.onerror = function (event) {
        console.error('🎤 完整语音收集错误:', event.error);
        if (silenceTimer) {
            clearTimeout(silenceTimer);
        }
        // 出错时也恢复播放
        resumePlayback();
    };

    // 启动完整语音收集
    try {
        completeRecognition.start();
    } catch (error) {
        console.error('🎤 启动完整语音收集失败:', error);
        resumePlayback();
    }
}

// 处理语音打断
async function handleSpeechInterruption(transcript) {
    console.log('检测到语音打断:', transcript);

    // 暂停当前播放
    isInterrupted = true;
    interruptedSegmentIndex = currentSegmentIndex;
    mainAudio.pause();
    stopSpeechRecognition();

    // 显示AI回答区域
    showAIResponse('正在处理您的问题...', true);

    try {
        // 发送问题到后端
        const response = await fetch('http://localhost:8080/api/voice-chat/ask', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                courseId: currentCourseId,
                question: transcript
            })
        });

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }

        const result = await response.json();

        // 显示AI回答
        showAIResponse(result.answer, false);

        // 获取语音合成
        const ttsResponse = await fetch('http://localhost:8080/api/voice-chat/synthesize', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                courseId: currentCourseId,
                text: result.answer
            })
        });

        if (ttsResponse.ok) {
            const audioBlob = await ttsResponse.blob();
            const audioUrl = URL.createObjectURL(audioBlob);

            const responseAudio = document.getElementById('responseAudio');
            responseAudio.src = audioUrl;

            // 播放AI回答
            responseAudio.play();

            // 监听AI回答播放完成
            responseAudio.onended = function () {
                // 回答播放完成，恢复原来的播放
                resumePlayback();
            };
        } else {
            // TTS失败，直接恢复播放
            setTimeout(() => {
                resumePlayback();
            }, 3000);
        }

    } catch (error) {
        console.error('处理语音打断失败:', error);
        showError('处理问题失败: ' + error.message);

        // 错误情况下也要恢复播放
        setTimeout(() => {
            resumePlayback();
        }, 2000);
    }
}

// 恢复播放
async function resumePlayback() {
    if (isInterrupted && interruptedSegmentIndex >= 0) {
        const interruptedSegment = audioSegments[interruptedSegmentIndex];
        const isFromBeginning = resumeMode === 'fromBeginning';

        console.log(`🔄 恢复播放模式: ${isFromBeginning ? '从句子开头' : '从打断位置'}`);
        console.log(`📝 被打断的句子: "${interruptedSegment?.textContent || '无文本'}"`);
        console.log(`⏰ 打断位置: ${interruptedPosition.toFixed(2)}s，恢复位置: ${isFromBeginning ? '0s' : interruptedPosition.toFixed(2) + 's'}`);

        // 隐藏AI回答区域
        hideAIResponse();

        // 恢复到被打断的片段
        currentSegmentIndex = interruptedSegmentIndex;

        try {
            // 恢复到被打断的片段，但从开头开始播放
            if (interruptedSegmentIndex >= 0 && interruptedSegmentIndex < splitAudioBlobs.length) {
                const audioBlob = splitAudioBlobs[interruptedSegmentIndex];

                if (!audioBlob || audioBlob.size === 0) {
                    throw new Error('被打断的片段音频为空');
                }

                // 先暂停当前播放，避免冲突
                if (!mainAudio.paused) {
                    mainAudio.pause();
                    await new Promise(resolve => setTimeout(resolve, 50));
                }

                const audioUrl = URL.createObjectURL(audioBlob);

                // 清除之前的事件监听器
                mainAudio.onended = null;
                mainAudio.onerror = null;
                mainAudio.onloadeddata = null;

                // 设置新的音频源
                mainAudio.src = audioUrl;

                // 等待音频加载完成
                await new Promise((resolve, reject) => {
                    const loadTimeout = setTimeout(() => {
                        reject(new Error('音频加载超时'));
                    }, 5000);

                    mainAudio.onloadeddata = () => {
                        clearTimeout(loadTimeout);

                        // 根据恢复模式设置播放位置
                        if (isFromBeginning) {
                            // 从句子开头开始播放（不设置 currentTime，默认为 0）
                            console.log(`🎵 音频加载完成，准备从句子开头播放片段 ${interruptedSegmentIndex + 1}`);
                        } else {
                            // 从打断位置开始播放
                            mainAudio.currentTime = interruptedPosition;
                            console.log(`🎵 音频加载完成，准备从打断位置 ${interruptedPosition.toFixed(2)}s 播放片段 ${interruptedSegmentIndex + 1}`);
                        }

                        resolve();
                    };

                    mainAudio.onerror = (e) => {
                        clearTimeout(loadTimeout);
                        reject(new Error('音频加载失败'));
                    };
                });

                // 设置播放结束事件监听器
                mainAudio.onended = function () {
                    console.log(`🔊 [resumePlayback] 音频片段 ${currentSegmentIndex + 1} 播放结束`);
                    onSegmentEnded();
                };

                mainAudio.onerror = function (e) {
                    console.error(`❌ [resumePlayback] 音频片段 ${currentSegmentIndex + 1} 播放错误:`, e);
                    setTimeout(() => {
                        if (isPlaying && !isInterrupted) {
                            onSegmentEnded();
                        }
                    }, 500);
                };

                // 开始播放
                await mainAudio.play();
                console.log(`🔄 ${isFromBeginning ? '从句子开头' : '从打断位置'}恢复播放片段 ${interruptedSegmentIndex + 1}`);
            } else {
                throw new Error('被打断的片段索引无效');
            }
        } catch (error) {
            console.error('恢复播放失败:', error);
            // 如果恢复失败，使用标准的播放方法
            playCurrentSegment();
        }

        // 重置打断状态并恢复播放状态
        isInterrupted = false;
        interruptedSegmentIndex = -1;
        interruptedPosition = 0;
        isPlaying = true; // 重要：恢复播放状态
        isPaused = false;

        console.log('🔄 播放状态已恢复:', {
            isPlaying,
            isInterrupted,
            enableVoiceDetectionFlag
        });
        logCurrentState('resumePlayback-after-state-reset');

        // 重新启动语音检测 - 延迟启动，确保音频播放稳定
        if (isPlaying && enableVoiceDetectionFlag) {
            console.log('🎤 准备重新启动语音检测...');
            setTimeout(() => {
                logCurrentState('resumePlayback-before-voice-detection-check');
                if (isPlaying && !isInterrupted && enableVoiceDetectionFlag) {
                    console.log('🎤 重新启动语音检测');
                    startVoiceDetection();
                    logCurrentState('resumePlayback-after-voice-detection-start');
                } else {
                    console.log('🔇 跳过启动语音检测 - 状态已改变:', {
                        isPlaying,
                        isInterrupted,
                        enableVoiceDetectionFlag
                    });
                }
            }, 1000); // 延迟1秒启动，确保音频播放稳定
        } else {
            console.log('🔇 不启动语音检测:', {
                isPlaying,
                enableVoiceDetectionFlag
            });
        }

        // 清空语音识别显示
        const transcriptDisplay = document.getElementById('transcriptDisplay');
        transcriptDisplay.textContent = '等待语音输入...';
        transcriptDisplay.classList.remove('has-content');

        // 更新状态显示和控制按钮
        updatePlaybackStatus();
        updatePlaybackControls();
    }
}

// 显示AI回答
function showAIResponse(content, isLoading) {
    const aiResponse = document.getElementById('aiResponse');
    const responseContent = document.getElementById('responseContent');
    const aiLoading = document.getElementById('aiLoading');

    responseContent.textContent = content;
    aiLoading.style.display = isLoading ? 'inline-block' : 'none';
    aiResponse.classList.add('show');
}

// 隐藏AI回答
function hideAIResponse() {
    const aiResponse = document.getElementById('aiResponse');
    aiResponse.classList.remove('show');
}

// 测试语音识别
function testSpeechRecognition() {
    const testBtn = document.getElementById('testBtn');

    if (isVoiceDetectionActive) {
        stopVoiceDetection();
        testBtn.textContent = '🎤 测试语音识别';
        testBtn.className = 'btn btn-primary';
    } else {
        // 清空之前的识别结果
        const transcriptDisplay = document.getElementById('transcriptDisplay');
        transcriptDisplay.textContent = '请开始说话...';
        transcriptDisplay.classList.remove('has-content');

        testBtn.textContent = '⏹️ 停止测试';
        testBtn.className = 'btn btn-danger';

        try {
            // 先请求麦克风权限
            navigator.mediaDevices.getUserMedia({ audio: true })
                .then(stream => {
                    console.log('麦克风权限确认');
                    stream.getTracks().forEach(track => track.stop());

                    // 启动语音检测
                    startVoiceDetection();
                })
                .catch(error => {
                    console.error('麦克风权限被拒绝:', error);
                    showError('麦克风权限被拒绝，请在浏览器地址栏左侧点击锁图标，允许麦克风访问');
                    testBtn.textContent = '🎤 测试语音识别';
                    testBtn.className = 'btn btn-primary';
                });
        } catch (error) {
            console.error('测试语音识别失败:', error);
            showError('测试语音识别失败: ' + error.message);
            testBtn.textContent = '🎤 测试语音识别';
            testBtn.className = 'btn btn-primary';
        }
    }
}

// 更新语音识别状态
function updateRecognitionStatus(status, isActive) {
    const recognitionStatus = document.getElementById('recognitionStatus');
    const recognitionIndicator = document.getElementById('recognitionIndicator');

    recognitionStatus.textContent = status;

    if (isActive) {
        recognitionIndicator.classList.add('active');
    } else {
        recognitionIndicator.classList.remove('active');
    }
}

// 更新交互状态
function updateInteractionStatus() {
    const interactionStatus = document.getElementById('interactionStatus');
    const interactionHint = document.getElementById('interactionHint');

    if (!interactionStatus || !interactionHint) return;

    if (isInterrupted) {
        interactionStatus.textContent = '🎤 语音打断中';
        interactionStatus.style.color = '#ff6b6b';
        interactionHint.textContent = '正在处理您的问题，请稍候...';
    } else if (isPlaying) {
        interactionStatus.textContent = '🎧 正在播放讲课 (可用唤醒词打断)';
        interactionStatus.style.color = '#28a745';
        if (enableWakeWordDetection) {
            interactionHint.textContent = '说"小思老师"、"老师"或"小思"即可打断讲课并提问';
        } else {
            interactionHint.textContent = '直接开口说话即可立即打断讲课并提问';
        }
    } else if (isPaused) {
        interactionStatus.textContent = '⏸️ 播放已暂停';
        interactionStatus.style.color = '#ffc107';
        interactionHint.textContent = '点击"开始播放"继续播放讲课';
    } else if (audioSegments.length > 0) {
        interactionStatus.textContent = '⏹️ 播放已停止';
        interactionStatus.style.color = '#6c757d';
        interactionHint.textContent = '点击"开始播放"开始播放讲课';
    } else {
        interactionStatus.textContent = '📋 等待加载音频';
        interactionStatus.style.color = '#6c757d';
        interactionHint.textContent = '请先输入会话ID和页码，然后加载音频数据';
    }
}

// 测试音频拆分功能
function testAudioSplit() {
    if (splitAudioBlobs.length === 0) {
        showError('请先加载页面音频数据');
        return;
    }

    console.log('🧪 开始测试音频拆分功能');

    // 详细分析每个片段
    let validSegments = 0;
    let emptySegments = 0;
    let totalSize = 0;

    console.log('📊 详细片段分析:');
    splitAudioBlobs.forEach((blob, index) => {
        const segment = audioSegments[index];
        const size = blob ? blob.size : 0;
        totalSize += size;

        if (size > 0) {
            validSegments++;
            console.log(`✅ 片段 ${index + 1}: ${(size / 1024).toFixed(1)}KB - "${segment?.textContent?.substring(0, 30) || '无文本'}..."`);
        } else {
            emptySegments++;
            console.log(`❌ 片段 ${index + 1}: 空片段 - "${segment?.textContent?.substring(0, 30) || '无文本'}..."`);
        }
    });

    // 显示拆分结果
    let testResult = `音频拆分详细测试结果：\n`;
    testResult += `- 原始音频片段数: ${audioSegments.length}\n`;
    testResult += `- 拆分音频片段数: ${splitAudioBlobs.length}\n`;
    testResult += `- 有效片段数: ${validSegments}\n`;
    testResult += `- 空片段数: ${emptySegments}\n`;
    testResult += `- 总音频大小: ${(totalSize / 1024).toFixed(1)}KB\n`;
    testResult += `- 拆分状态: ${splitAudioBlobs.length === audioSegments.length ? '✅ 数量匹配' : '⚠️ 数量不匹配'}\n`;
    testResult += `- 质量状态: ${emptySegments === 0 ? '✅ 无空片段' : `⚠️ 有${emptySegments}个空片段`}\n\n`;

    if (emptySegments > 0) {
        testResult += `空片段详情：\n`;
        splitAudioBlobs.forEach((blob, index) => {
            if (!blob || blob.size === 0) {
                const segment = audioSegments[index];
                testResult += `- 片段 ${index + 1}: "${segment?.textContent?.substring(0, 30) || '无文本'}..."\n`;
            }
        });
    }

    alert(testResult);
    console.log('🧪 音频拆分测试完成');
}

// 验证播放序列的完整性
function validatePlaybackSequence() {
    console.log('🔍 验证播放序列完整性...');

    const issues = [];

    // 检查片段数量匹配
    if (audioSegments.length !== splitAudioBlobs.length) {
        issues.push(`片段数量不匹配: 元数据${audioSegments.length}个，音频${splitAudioBlobs.length}个`);
    }

    // 检查每个片段的有效性
    for (let i = 0; i < Math.max(audioSegments.length, splitAudioBlobs.length); i++) {
        const segment = audioSegments[i];
        const blob = splitAudioBlobs[i];

        if (!segment) {
            issues.push(`片段 ${i + 1}: 缺少元数据`);
        }

        if (!blob || blob.size === 0) {
            issues.push(`片段 ${i + 1}: 音频为空或缺失`);
        }

        if (segment && (!segment.textContent || segment.textContent.trim().length === 0)) {
            issues.push(`片段 ${i + 1}: 文本内容为空`);
        }
    }

    if (issues.length === 0) {
        console.log('✅ 播放序列验证通过，所有片段完整');
        showSuccess('播放序列验证通过');
    } else {
        console.warn('⚠️ 播放序列存在问题:');
        issues.forEach(issue => console.warn(`  - ${issue}`));
        showError(`发现 ${issues.length} 个播放序列问题，请检查控制台`);
    }

    return issues.length === 0;
}

// 更新播放控制按钮状态
function updatePlaybackControls() {
    const playBtn = document.getElementById('playBtn');
    const pauseBtn = document.getElementById('pauseBtn');
    const stopBtn = document.getElementById('stopBtn');
    const testAudioBtn = document.getElementById('testAudioBtn');
    const validateBtn = document.getElementById('validateBtn');

    playBtn.disabled = isPlaying || audioSegments.length === 0;
    pauseBtn.disabled = !isPlaying;
    stopBtn.disabled = !isPlaying && !isPaused;
    testAudioBtn.disabled = splitAudioBlobs.length === 0;
    validateBtn.disabled = splitAudioBlobs.length === 0;
}

// 启用播放控制
function enablePlaybackControls() {
    updatePlaybackControls();
}

// 更新播放状态显示
function updatePlaybackStatus() {
    const playbackStatus = document.getElementById('playbackStatus');
    const currentSegment = document.getElementById('currentSegment');
    const totalSegments = document.getElementById('totalSegments');
    const playbackProgress = document.getElementById('playbackProgress');
    const progressFill = document.getElementById('progressFill');
    const audioSegmentStatus = document.getElementById('audioSegmentStatus');

    let status = '未开始';
    if (isInterrupted) {
        status = '已打断';
    } else if (isPlaying) {
        status = '播放中';
    } else if (isPaused) {
        status = '已暂停';
    }

    playbackStatus.textContent = status;
    currentSegment.textContent = audioSegments.length > 0 ? `${currentSegmentIndex + 1}` : '-';
    totalSegments.textContent = audioSegments.length.toString();

    const progress = audioSegments.length > 0 ? Math.round((currentSegmentIndex / audioSegments.length) * 100) : 0;
    playbackProgress.textContent = `${progress}%`;
    progressFill.style.width = `${progress}%`;

    // 更新音频片段状态
    if (splitAudioBlobs.length === 0) {
        audioSegmentStatus.textContent = '未加载';
    } else if (splitAudioBlobs.length === audioSegments.length) {
        audioSegmentStatus.textContent = `已拆分 ${splitAudioBlobs.length} 个片段`;
    } else {
        audioSegmentStatus.textContent = `拆分异常 (${splitAudioBlobs.length}/${audioSegments.length})`;
    }

    // 更新交互状态
    updateInteractionStatus();
}

// 格式化时长
function formatDuration(milliseconds) {
    const seconds = Math.floor(milliseconds / 1000);
    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;
    return `${minutes}:${remainingSeconds.toString().padStart(2, '0')}`;
}

// 显示错误消息
function showError(message) {
    const errorDiv = document.getElementById('errorMessage');
    const successDiv = document.getElementById('successMessage');

    errorDiv.textContent = message;
    errorDiv.style.display = 'block';
    successDiv.style.display = 'none';

    setTimeout(() => {
        errorDiv.style.display = 'none';
    }, 5000);
}

// 显示成功消息
function showSuccess(message) {
    const successDiv = document.getElementById('successMessage');
    const errorDiv = document.getElementById('errorMessage');

    successDiv.textContent = message;
    successDiv.style.display = 'block';
    errorDiv.style.display = 'none';

    setTimeout(() => {
        successDiv.style.display = 'none';
    }, 3000);
}

// 调试状态跟踪函数
function logCurrentState(context) {
    console.log(`🔍 [${context}] 当前状态:`, {
        isPlaying,
        isPaused,
        isInterrupted,
        isRecognizing,
        isVoiceDetectionActive,
        enableVoiceDetectionFlag,
        currentSegmentIndex,
        interruptedSegmentIndex,
        interruptedPosition
    });
}

// 更新语音检测设置
function updateVoiceDetection() {
    const checkbox = document.getElementById('enableVoiceDetection');
    enableVoiceDetectionFlag = checkbox.checked;

    console.log('语音检测设置更新:', enableVoiceDetectionFlag ? '启用' : '禁用');
    logCurrentState('updateVoiceDetection');

    if (!enableVoiceDetectionFlag && isVoiceDetectionActive) {
        // 如果禁用语音检测且当前正在检测，则停止
        stopVoiceDetection();
    } else if (enableVoiceDetectionFlag && isPlaying && !isVoiceDetectionActive) {
        // 如果启用语音检测且正在播放但未检测，则启动
        startVoiceDetection();
    }
}

// 更新恢复模式设置
function updateResumeMode() {
    const selectedMode = document.querySelector('input[name="resumeMode"]:checked');
    if (selectedMode) {
        resumeMode = selectedMode.value;
        console.log('播放恢复模式更新:', resumeMode === 'fromBeginning' ? '从句子开头恢复' : '从打断位置恢复');

        // 显示用户友好的提示
        if (resumeMode === 'fromBeginning') {
            showSuccess('已设置为从句子开头恢复播放，体验更自然流畅');
        } else {
            showSuccess('已设置为从打断位置恢复播放，保持精确位置');
        }
    }
}

// 更新唤醒词检测设置
function updateWakeWordDetection() {
    const checkbox = document.getElementById('enableWakeWordDetection');
    enableWakeWordDetection = checkbox.checked;

    console.log('唤醒词检测设置更新:', enableWakeWordDetection ? '启用' : '禁用');

    // 显示用户友好的提示
    if (enableWakeWordDetection) {
        showSuccess(`已启用唤醒词检测，需要说"${WAKE_WORDS.join('"、"')}"才能打断讲课`);
    } else {
        showSuccess('已禁用唤醒词检测，任何语音都可以打断讲课');
    }
}

// 启动语音检测
function startVoiceDetection() {
    // 检查基本条件
    if (!enableVoiceDetectionFlag) {
        console.log('🔇 语音检测已禁用，跳过启动');
        return;
    }

    // 检查播放状态
    if (!isPlaying || isInterrupted) {
        console.log('🔇 播放状态不合适，跳过启动语音检测:', {
            isPlaying,
            isInterrupted
        });
        return;
    }

    if (!speechRecognition && !initializeSpeechRecognition()) {
        console.log('🔇 语音识别初始化失败');
        return;
    }

    if (isRecognizing || isVoiceDetectionActive) {
        console.log('🎤 语音识别已在运行中，跳过启动:', {
            isRecognizing,
            isVoiceDetectionActive
        });
        return;
    }

    try {
        isVoiceDetectionActive = true;
        networkErrorCount = 0;
        finalTranscript = '';
        interimTranscript = '';

        speechRecognition.start();
        console.log('🎤 启动语音检测');
        updateRecognitionStatus('等待语音输入...', true);
    } catch (error) {
        console.error('启动语音检测失败:', error);
        isVoiceDetectionActive = false;
        updateRecognitionStatus('启动语音检测失败: ' + error.message, false);
    }
}

// 停止语音检测
function stopVoiceDetection() {
    console.log('🔇 停止语音检测');

    // 停止语音识别
    if (speechRecognition && isRecognizing) {
        try {
            speechRecognition.stop();
        } catch (error) {
            console.error('停止语音识别失败:', error);
        }
    }

    // 清理状态
    isVoiceDetectionActive = false;
    isRecognizing = false;
    finalTranscript = '';
    interimTranscript = '';

    // 清理计时器
    clearRecognitionTimeout();

    // 更新UI状态
    updateRecognitionStatus('语音检测已停止', false);

    // 清空识别显示
    const transcriptDisplay = document.getElementById('transcriptDisplay');
    if (transcriptDisplay) {
        transcriptDisplay.textContent = '等待语音输入...';
        transcriptDisplay.classList.remove('has-content');
        transcriptDisplay.style.borderColor = '#dee2e6';
    }
}

// 处理语音识别结果
function handleVoiceRecognitionResult(recognizedText) {
    console.log('🗣️ 处理语音识别结果:', recognizedText);

    // 严格验证识别结果
    if (!recognizedText || typeof recognizedText !== 'string' || recognizedText.trim().length < 2) {
        console.log('❌ 识别结果无效，忽略:', {
            text: recognizedText,
            type: typeof recognizedText,
            length: recognizedText ? recognizedText.trim().length : 0
        });
        return;
    }

    const cleanText = recognizedText.trim();

    // 过滤明显的误识别（如单个字符、数字等）
    if (cleanText.length < 2 || /^[0-9\s\.,!?]+$/.test(cleanText)) {
        console.log('❌ 识别结果疑似误识别，忽略:', cleanText);
        return;
    }

    console.log('✅ 识别结果有效:', cleanText);

    // 显示识别结果
    const transcriptDisplay = document.getElementById('transcriptDisplay');
    if (transcriptDisplay) {
        transcriptDisplay.textContent = cleanText;
        transcriptDisplay.classList.add('has-content');
        transcriptDisplay.style.borderColor = '#28a745';
    }

    // 检查是否包含唤醒词
    if (!containsWakeWord(cleanText)) {
        console.log('🔇 未检测到唤醒词，忽略语音:', cleanText);
        // 更新显示状态，表明检测到语音但没有唤醒词
        if (transcriptDisplay) {
            transcriptDisplay.style.borderColor = '#ffc107'; // 黄色边框表示检测到语音但未唤醒
            transcriptDisplay.textContent = `🔇 ${cleanText} (未包含唤醒词)`;
        }
        return;
    }

    // 提取问题内容（去除唤醒词）
    const question = extractQuestionFromTranscript(cleanText);

    if (!question || question.length < 1) {
        console.log('🔇 提取问题后内容为空，忽略');
        if (transcriptDisplay) {
            transcriptDisplay.style.borderColor = '#ffc107';
            transcriptDisplay.textContent = `🔇 ${cleanText} (仅包含唤醒词，无问题内容)`;
        }
        return;
    }

    console.log('🎯 检测到唤醒词，提取的问题:', question);

    // 更新显示，显示提取的问题
    if (transcriptDisplay) {
        transcriptDisplay.textContent = `🎤 ${question}`;
        transcriptDisplay.style.borderColor = '#28a745'; // 绿色边框表示成功唤醒
    }

    // 如果正在播放且未被打断，立即打断
    if (isPlaying && !isInterrupted) {
        console.log('🎤 触发语音打断');
        handleVoiceInterruption(question); // 使用提取的问题而不是原始文本
    } else {
        console.log('🔇 不触发打断:', {
            isPlaying,
            isInterrupted
        });
    }
}

// 重置识别超时计时器
function resetRecognitionTimeout() {
    clearRecognitionTimeout();
    recognitionTimeout = setTimeout(() => {
        if (isRecognizing && !interimTranscript && !finalTranscript) {
            console.log('⏰ 识别超时，重新启动');
            speechRecognition.stop();
        }
    }, RECOGNITION_TIMEOUT);
}

// 清除识别超时计时器
function clearRecognitionTimeout() {
    if (recognitionTimeout) {
        clearTimeout(recognitionTimeout);
        recognitionTimeout = null;
    }
}

// 重启语音识别
function restartSpeechRecognition() {
    // 严格的条件检查
    if (!isVoiceDetectionActive || !speechRecognition || isRecognizing) {
        console.log('🔇 跳过重启语音识别 - 条件不满足:', {
            isVoiceDetectionActive,
            hasSpeechRecognition: !!speechRecognition,
            isRecognizing
        });
        return;
    }

    // 只有在播放且未被打断时才重启
    if (!isPlaying || isInterrupted) {
        console.log('🔇 跳过重启语音识别 - 播放状态不合适:', {
            isPlaying,
            isInterrupted
        });
        return;
    }

    try {
        speechRecognition.start();
        console.log('🔄 重启语音识别');
    } catch (error) {
        console.error('重启语音识别失败:', error);
        // 减少重试频率，避免无限循环
        setTimeout(() => {
            if (isVoiceDetectionActive && isPlaying && !isInterrupted) {
                restartSpeechRecognition();
            }
        }, 2000); // 增加延迟到2秒
    }
}

// 处理语音打断
async function handleVoiceInterruption(question) {
    console.log('🎤 检测到语音，打断讲课:', question);

    // 暂停当前播放并记录位置
    if (mainAudio && !mainAudio.paused) {
        isInterrupted = true;
        interruptedSegmentIndex = currentSegmentIndex;
        interruptedPosition = mainAudio.currentTime; // 记录打断时的播放位置
        mainAudio.pause();
        console.log(`⏸️ 暂停讲课，片段 ${interruptedSegmentIndex + 1}，位置 ${interruptedPosition.toFixed(2)}s`);
    } else {
        isInterrupted = true;
        interruptedSegmentIndex = currentSegmentIndex;
        interruptedPosition = 0;
    }

    // 停止语音检测
    stopVoiceDetection();

    // 更新UI状态
    updateInteractionStatus();
    showAIResponse('正在处理您的问题...', true);

    try {
        // 发送问题到后端
        const response = await fetch('http://localhost:8080/api/voice-chat/ask', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                courseId: currentCourseId,
                question: question
            })
        });

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }

        const result = await response.json();

        // 显示AI回答
        showAIResponse(result.answer, false);

        // 获取语音合成
        const ttsResponse = await fetch('http://localhost:8080/api/voice-chat/synthesize', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                courseId: currentCourseId,
                text: result.answer
            })
        });

        if (ttsResponse.ok) {
            const audioBlob = await ttsResponse.blob();
            const audioUrl = URL.createObjectURL(audioBlob);

            const responseAudio = document.getElementById('responseAudio');
            responseAudio.src = audioUrl;

            // 播放AI回答
            responseAudio.play();

            // 监听AI回答播放完成
            responseAudio.onended = function () {
                // 回答播放完成，恢复原来的播放
                resumePlayback();
            };
        } else {
            // TTS失败，直接恢复播放
            setTimeout(() => {
                resumePlayback();
            }, 3000);
        }

    } catch (error) {
        console.error('处理语音打断失败:', error);
        showError('处理问题失败: ' + error.message);

        // 错误情况下也要恢复播放
        setTimeout(() => {
            resumePlayback();
        }, 2000);
    }
}

// ==================== 批量音频合成功能 ====================

// 开始批量合成
async function startBulkSynthesis() {
    const jsonInput = document.getElementById('bulkSynthesisJson').value.trim();

    if (!jsonInput) {
        showError('请输入PPT数据的JSON格式');
        return;
    }

    let requestData;
    try {
        // 解析JSON数据
        requestData = JSON.parse(jsonInput);
    } catch (error) {
        showError('JSON格式错误: ' + error.message);
        return;
    }

    // 验证必需字段
    if (!requestData.title || !requestData.slides || !Array.isArray(requestData.slides)) {
        showError('JSON数据缺少必需字段: title 和 slides');
        return;
    }

    // 获取合成选项
    const options = {
        enablePolishing: document.getElementById('enablePolishing').checked,
        saveOriginalText: document.getElementById('saveOriginalText').checked,
        audioFormat: document.getElementById('audioFormat').value,
        sampleRate: parseInt(document.getElementById('sampleRate').value)
    };

    // 构建完整的请求数据
    const fullRequestData = {
        ...requestData,
        options: options
    };

    console.log('🎵 开始批量音频合成:', fullRequestData);

    // 显示合成状态
    showSynthesisStatus('正在提交批量合成请求...', true);

    try {
        const response = await fetch('http://localhost:8080/api/speech/bulk-synthesis', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(fullRequestData)
        });

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }

        const result = await response.json();
        console.log('✅ 批量合成请求成功:', result);

        // 显示成功状态
        showSynthesisStatus(`批量合成已启动！状态: ${result.status}`, false);
        updateSynthesisDetails(result);

        // 显示会话ID
        if (result.courseId) {
            showCourseId(result.courseId);
            showSuccess(`批量合成已启动，会话ID: ${result.courseId}`);
        }

    } catch (error) {
        console.error('❌ 批量合成请求失败:', error);
        showSynthesisStatus('批量合成请求失败: ' + error.message, false);
        showError('批量合成失败: ' + error.message);
    }
}

// 显示合成状态
function showSynthesisStatus(message, isLoading) {
    const statusDiv = document.getElementById('synthesisStatus');
    const statusText = document.getElementById('synthesisStatusText');
    const loadingDiv = document.getElementById('synthesisLoading');

    statusText.textContent = message;
    loadingDiv.style.display = isLoading ? 'inline-block' : 'none';
    statusDiv.style.display = 'block';
}

// 更新合成详情
function updateSynthesisDetails(result) {
    const detailsDiv = document.getElementById('synthesisDetails');
    let details = '';

    if (result.totalSlides) {
        details += `总页数: ${result.totalSlides}`;
    }
    if (result.totalContentPoints) {
        details += ` | 总内容点: ${result.totalContentPoints}`;
    }
    if (result.startTime) {
        details += ` | 开始时间: ${new Date(result.startTime).toLocaleString()}`;
    }
    if (result.message) {
        details += ` | ${result.message}`;
    }

    detailsDiv.textContent = details;
}

// 显示会话ID
function showCourseId(courseId) {
    const courseIdDiv = document.getElementById('synthesisCourseId');
    const courseIdValue = document.getElementById('courseIdValue');

    courseIdValue.textContent = courseId;
    courseIdDiv.style.display = 'block';
}

// 复制会话ID
function copyCourseId() {
    const courseId = document.getElementById('courseIdValue').textContent;
    navigator.clipboard.writeText(courseId).then(() => {
        showSuccess('会话ID已复制到剪贴板');
    }).catch(err => {
        console.error('复制失败:', err);
        showError('复制失败，请手动复制');
    });
}

// 加载示例数据
function loadSampleData() {
    const sampleData = {
        "title": "人工智能基础知识",
        "slides": [
            {
                "page_number": 1,
                "title": "什么是人工智能",
                "content_points": [
                    "人工智能是计算机科学的一个分支",
                    "它致力于创建能够执行通常需要人类智能的任务的系统",
                    "包括学习、推理、感知和语言理解等能力"
                ],
                "slide_type": "introduction",
                "type": "concept",
                "description": "介绍人工智能的基本概念"
            },
            {
                "page_number": 2,
                "title": "机器学习的类型",
                "content_points": [
                    "监督学习：使用标记数据进行训练",
                    "无监督学习：从未标记数据中发现模式",
                    "强化学习：通过与环境交互来学习最优策略"
                ],
                "slide_type": "content",
                "type": "classification",
                "description": "介绍机器学习的主要类型"
            },
            {
                "page_number": 3,
                "title": "深度学习应用",
                "content_points": [
                    "图像识别：在医疗诊断和自动驾驶中的应用",
                    "自然语言处理：机器翻译和智能客服",
                    "语音识别：语音助手和语音转文字技术"
                ],
                "slide_type": "application",
                "type": "examples",
                "description": "展示深度学习的实际应用场景"
            }
        ]
    };

    document.getElementById('bulkSynthesisJson').value = JSON.stringify(sampleData, null, 2);
    showSuccess('示例数据已加载');
}

// 验证JSON格式
function validateJsonFormat() {
    const jsonInput = document.getElementById('bulkSynthesisJson').value.trim();

    if (!jsonInput) {
        showError('请先输入JSON数据');
        return;
    }

    try {
        const data = JSON.parse(jsonInput);

        // 验证必需字段
        const errors = [];

        if (!data.title || typeof data.title !== 'string') {
            errors.push('缺少或无效的 title 字段');
        }

        if (!data.slides || !Array.isArray(data.slides)) {
            errors.push('缺少或无效的 slides 字段（应为数组）');
        } else {
            data.slides.forEach((slide, index) => {
                if (!slide.page_number || typeof slide.page_number !== 'number') {
                    errors.push(`第${index + 1}个slide缺少或无效的 page_number 字段`);
                }
                if (!slide.title || typeof slide.title !== 'string') {
                    errors.push(`第${index + 1}个slide缺少或无效的 title 字段`);
                }
                if (!slide.content_points || !Array.isArray(slide.content_points)) {
                    errors.push(`第${index + 1}个slide缺少或无效的 content_points 字段（应为数组）`);
                }
            });
        }

        if (errors.length > 0) {
            showError('JSON验证失败:\n' + errors.join('\n'));
        } else {
            showSuccess(`JSON格式验证通过！包含 ${data.slides.length} 个幻灯片`);
        }

    } catch (error) {
        showError('JSON格式错误: ' + error.message);
    }
}

// 清空JSON输入
function clearJsonInput() {
    document.getElementById('bulkSynthesisJson').value = '';
    document.getElementById('synthesisStatus').style.display = 'none';
    showSuccess('输入已清空');
}
// 加
载目录页面测试数据
function loadAgendaTestData() {
    const testData = {
        "title": "SOA架构与微服务实践课程",
        "slides": [
            {
                "pageNumber": 1,
                "title": "课程目录",
                "slideType": "agenda",
                "description": "PPT目录页，列出演讲的主要内容",
                "contentPoints": [
                    "什么是SOA？",
                    "SOA架构核心组件",
                    "SOA与微服务的深入比较",
                    "Spring Cloud在SOA中的应用",
                    "服务治理与监控",
                    "深入探讨SOA安全策略及最佳实践",
                    "企业级SOA实施案例分析：成功与失败案例",
                    "SOA架构的挑战与未来趋势",
                    "Spring Cloud最佳实践与技术选型",
                    "SOA接口规范与设计：WSDL和RESTful API",
                    "总结与展望",
                    "问答环节",
                    "参考资料",
                    "联系方式"
                ]
            },
            {
                "pageNumber": 2,
                "title": "什么是SOA？",
                "slideType": "content",
                "description": "介绍SOA的基本概念和特点",
                "contentPoints": [
                    "SOA（Service-Oriented Architecture）是一种软件架构风格",
                    "强调通过服务的方式组织和访问分布式功能",
                    "服务是松耦合的、可重用的软件组件",
                    "支持跨平台、跨语言的系统集成"
                ]
            }
        ],
        "options": {
            "enablePolishing": true,
            "saveOriginalText": true,
            "audioFormat": "wav",
            "sampleRate": 16000
        }
    };

    const textarea = document.getElementById('bulkSynthesisJson');
    if (textarea) {
        textarea.value = JSON.stringify(testData, null, 2);
        showSuccess('已加载目录页面测试数据，可以测试字数控制效果');
        console.log('📋 加载目录页面测试数据完成');
    } else {
        showError('未找到JSON输入框');
    }
}

// 加载示例数据（如果还没有这个函数）
function loadSampleData() {
    const sampleData = {
        "title": "示例课程",
        "slides": [
            {
                "pageNumber": 1,
                "title": "课程介绍",
                "slideType": "title",
                "description": "课程标题页",
                "contentPoints": [
                    "欢迎参加本次课程",
                    "本课程将介绍相关技术概念",
                    "希望大家积极参与讨论"
                ]
            }
        ],
        "options": {
            "enablePolishing": true,
            "saveOriginalText": true,
            "audioFormat": "wav",
            "sampleRate": 16000
        }
    };

    const textarea = document.getElementById('bulkSynthesisJson');
    if (textarea) {
        textarea.value = JSON.stringify(sampleData, null, 2);
        showSuccess('已加载示例数据');
    } else {
        showError('未找到JSON输入框');
    }
}

// 验证JSON格式（如果还没有这个函数）
function validateJsonFormat() {
    const textarea = document.getElementById('bulkSynthesisJson');
    if (!textarea) {
        showError('未找到JSON输入框');
        return;
    }

    const jsonText = textarea.value.trim();
    if (!jsonText) {
        showError('请先输入JSON数据');
        return;
    }

    try {
        const data = JSON.parse(jsonText);
        
        // 验证必需字段
        if (!data.title) {
            showError('JSON缺少必需字段: title');
            return;
        }
        
        if (!data.slides || !Array.isArray(data.slides)) {
            showError('JSON缺少必需字段: slides (数组)');
            return;
        }

        if (data.slides.length === 0) {
            showError('slides数组不能为空');
            return;
        }

        // 验证每个slide
        for (let i = 0; i < data.slides.length; i++) {
            const slide = data.slides[i];
            if (!slide.pageNumber) {
                showError(`slide[${i}] 缺少必需字段: pageNumber`);
                return;
            }
            if (!slide.title) {
                showError(`slide[${i}] 缺少必需字段: title`);
                return;
            }
            if (!slide.contentPoints || !Array.isArray(slide.contentPoints)) {
                showError(`slide[${i}] 缺少必需字段: contentPoints (数组)`);
                return;
            }
        }

        showSuccess(`JSON格式验证通过！包含 ${data.slides.length} 个页面`);
        console.log('✅ JSON验证通过:', data);
        
    } catch (error) {
        showError('JSON格式错误: ' + error.message);
        console.error('❌ JSON解析错误:', error);
    }
}

// 清空JSON输入（如果还没有这个函数）
function clearJsonInput() {
    const textarea = document.getElementById('bulkSynthesisJson');
    if (textarea) {
        textarea.value = '';
        showSuccess('已清空JSON输入');
    } else {
        showError('未找到JSON输入框');
    }
}

// 开始批量合成（如果还没有这个函数）
async function startBulkSynthesis() {
    const textarea = document.getElementById('bulkSynthesisJson');
    if (!textarea) {
        showError('未找到JSON输入框');
        return;
    }

    const jsonText = textarea.value.trim();
    if (!jsonText) {
        showError('请先输入JSON数据');
        return;
    }

    let requestData;
    try {
        requestData = JSON.parse(jsonText);
    } catch (error) {
        showError('JSON格式错误: ' + error.message);
        return;
    }

    // 验证必需字段
    if (!requestData.title || !requestData.slides) {
        showError('JSON数据缺少必需字段: title 和 slides');
        return;
    }

    // 获取合成选项
    const options = {
        enablePolishing: document.getElementById('enablePolishing').checked,
        saveOriginalText: document.getElementById('saveOriginalText').checked,
        audioFormat: document.getElementById('audioFormat').value,
        sampleRate: parseInt(document.getElementById('sampleRate').value)
    };

    // 构建完整的请求数据
    const fullRequestData = {
        ...requestData,
        options: options
    };

    console.log('🎵 开始批量音频合成:', fullRequestData);

    // 显示合成状态
    showSynthesisStatus('正在提交批量合成请求...', true);

    try {
        const response = await fetch('http://localhost:8080/api/speech/bulk-synthesis', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(fullRequestData)
        });

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }

        const result = await response.json();
        console.log('✅ 批量合成请求成功:', result);

        // 显示成功状态
        showSynthesisStatus(`批量合成已启动！状态: ${result.status}`, false);
        updateSynthesisDetails(result);

        // 显示会话ID
        if (result.courseId) {
            showCourseId(result.courseId);
            showSuccess(`批量合成已启动，会话ID: ${result.courseId}`);
        }

    } catch (error) {
        console.error('❌ 批量合成请求失败:', error);
        showSynthesisStatus('批量合成请求失败: ' + error.message, false);
        showError('批量合成失败: ' + error.message);
    }
}

// 显示合成状态
function showSynthesisStatus(message, isLoading) {
    const statusDiv = document.getElementById('synthesisStatus');
    const statusText = document.getElementById('synthesisStatusText');
    const loadingDiv = document.getElementById('synthesisLoading');

    if (statusDiv && statusText) {
        statusDiv.style.display = 'block';
        statusText.textContent = message;
        
        if (loadingDiv) {
            loadingDiv.style.display = isLoading ? 'inline-block' : 'none';
        }
    }
}

// 更新合成详情
function updateSynthesisDetails(result) {
    const detailsDiv = document.getElementById('synthesisDetails');
    if (detailsDiv && result) {
        let details = '';
        if (result.totalSlides) {
            details += `总页数: ${result.totalSlides}`;
        }
        if (result.totalContentPoints) {
            details += `, 总内容点: ${result.totalContentPoints}`;
        }
        if (result.message) {
            details += `, ${result.message}`;
        }
        detailsDiv.textContent = details;
    }
}

// 显示会话ID
function showCourseId(courseId) {
    const sessionDiv = document.getElementById('synthesisCourseId');
    const sessionValue = document.getElementById('courseIdValue');
    
    if (sessionDiv && sessionValue) {
        sessionValue.textContent = courseId;
        sessionDiv.style.display = 'block';
    }
}

// 复制会话ID
function copyCourseId() {
    const sessionValue = document.getElementById('courseIdValue');
    if (sessionValue) {
        const courseId = sessionValue.textContent;
        navigator.clipboard.writeText(courseId).then(() => {
            showSuccess('会话ID已复制到剪贴板');
        }).catch(err => {
            console.error('复制失败:', err);
            showError('复制失败，请手动复制');
        });
    }
}