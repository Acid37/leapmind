// 生成卡通风格的HTML PPT内容
const generateSlideHTML = (title, emoji, content, examples, color) => {
    return `
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: 'Comic Sans MS', 'Microsoft YaHei', 'Apple Color Emoji', 'Segoe UI Emoji', 'Segoe UI Symbol', sans-serif;
           // background: linear-gradient(135deg, ${color.start} 0%, ${color.end} 100%);
            width: 1280px;
            height: 720px;
            margin: 0;
            padding: 0;
             overflow: hidden;
            // position: relative;
        }
        .slide-container {
            background: white;
            //border-radius: 25px;
            padding: 35px;
            width: 1250px;
            height: 680px;
            box-shadow: 0 20px 60px rgba(0,0,0,0.3);
            position: absolute;
            top: 50%;
            left: 50%;
            transform: translate(-50%, -50%);
            animation: slideIn 0.6s ease-out;
            display: flex;
            flex-direction: column;
            justify-content: space-between;
            overflow: hidden;
        }
        @keyframes slideIn {
            from { transform: translate(-50%, calc(-50% + 30px)); opacity: 0; }
            to { transform: translate(-50%, -50%); opacity: 1; }
        }
        .header {
            text-align: center;
            margin-bottom: 15px;
            flex-shrink: 0;
        }
        .title {
            font-size: 36px;
            font-weight: bold;
            color: ${color.text};
            margin-bottom: 8px;
            text-shadow: 3px 3px 0px rgba(0,0,0,0.1);
            line-height: 1.2;
        }
        .emoji {
            font-size: 60px;
            display: inline-block;
            animation: bounce 2s infinite;
            font-family: 'Apple Color Emoji', 'Segoe UI Emoji', 'Segoe UI Symbol', 'Noto Color Emoji', 'EmojiOne Color', sans-serif;
            font-style: normal;
            font-variant: normal;
            text-rendering: auto;
            -webkit-font-smoothing: antialiased;
            line-height: 1;
        }
        @keyframes bounce {
            0%, 100% { transform: translateY(0); }
            50% { transform: translateY(-12px); }
        }
        .content {
            font-size: 20px;
            line-height: 1.5;
            color: #333;
            margin-bottom: 15px;
            text-align: center;
            flex-shrink: 0;
        }
        .examples {
            background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
            border-radius: 18px;
            padding: 20px;
            flex: 1;
            display: flex;
            flex-direction: column;
            justify-content: center;
            overflow: hidden;
            min-height: 0;
        }
        .example-item {
            background: white;
            border-radius: 12px;
            padding: 14px;
            margin: 8px 0;
            box-shadow: 0 5px 15px rgba(0,0,0,0.1);
            display: flex;
            align-items: center;
            gap: 16px;
            font-size: 20px;
            font-weight: bold;
            flex-shrink: 0;
        }
        .number {
            background: ${color.accent};
            color: white;
            width: 48px;
            height: 48px;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 24px;
            box-shadow: 0 5px 15px rgba(0,0,0,0.2);
            flex-shrink: 0;
        }
        .decoration {
            position: absolute;
            font-size: 100px;
            opacity: 0.08;
            pointer-events: none;
            z-index: 0;
            font-family: 'Apple Color Emoji', 'Segoe UI Emoji', 'Segoe UI Symbol', 'Noto Color Emoji', 'EmojiOne Color', sans-serif;
            font-style: normal;
            line-height: 1;
        }
        .decoration-1 { top: 8px; left: 8px; }
        .decoration-2 { bottom: 8px; right: 8px; }
        .header, .content, .examples {
            position: relative;
            z-index: 1;
        }
    </style>
</head>
<body>
    <div class="slide-container">
        <div class="decoration decoration-1">${emoji}</div>
        <div class="decoration decoration-2">${emoji}</div>
        <div class="header">
            <div class="title">${title}</div>
            <div class="emoji">${emoji}</div>
        </div>
        <div class="content">${content}</div>
        <div class="examples">
            ${examples.map(ex => `
                <div class="example-item">
                    <div class="number">${ex.number}</div>
                    <div>${ex.text}</div>
                </div>
            `).join('')}
        </div>
    </div>
</body>
</html>
    `.trim();
};

const slidesData = [
    {
        title: "什么是正数和负数？",
        description: "正数和负数是数学中表示相反意义的数。正数用'+'号表示，负数用'-'号表示。",
        tags: ["正数", "负数", "相反数"],
        subtitles: [
            "同学们好！今天我们来认识正数和负数。",
            "正数就像向上爬楼梯，负数就像向下走楼梯。",
            "它们是一对好朋友，总是表示相反的意思！"
        ],
        code: `正数：+1, +2, +3, +10...
负数：-1, -2, -3, -10...
零：0（既不是正数也不是负数）`,
        html_content: generateSlideHTML(
            "什么是正数和负数？",
            "🔢",
            "正数和负数是数学中表示相反意义的数！正数用'+'号表示，负数用'-'号表示。",
            [
                { number: "+5", text: "正数：表示增加、上升、盈利" },
                { number: "-5", text: "负数：表示减少、下降、亏损" },
                { number: "0", text: "零：既不是正数也不是负数" }
            ],
            { start: "#FF6B9D", end: "#C44569", text: "#C44569", accent: "#FF6B9D" }
        )
    },
    {
        title: "数轴上的正数和负数",
        description: "数轴是一条直线，可以帮助我们直观地理解正数和负数的位置关系。",
        tags: ["数轴", "位置", "比较"],
        subtitles: [
            "数轴就像一条神奇的道路！",
            "正数在零的右边，负数在零的左边。",
            "离零越远，数字的绝对值就越大！"
        ],
        code: `数轴示例：
← -3  -2  -1  0  +1  +2  +3 →
        左边    原点    右边`,
        html_content: generateSlideHTML(
            "数轴上的正数和负数",
            "📏",
            "数轴是一条直线，帮助我们直观地理解正数和负数的位置关系！",
            [
                { number: "←", text: "负数在零的左边（-3, -2, -1）" },
                { number: "0", text: "零是正数和负数的分界点" },
                { number: "→", text: "正数在零的右边（+1, +2, +3）" }
            ],
            { start: "#4ECDC4", end: "#44A08D", text: "#44A08D", accent: "#4ECDC4" }
        )
    },
    {
        title: "正数和负数的加减法",
        description: "正数和负数相加时，同号相加，异号相减。记住：正数加负数，看谁的绝对值大！",
        tags: ["加法", "减法", "运算"],
        subtitles: [
            "现在我们来学习正数和负数的加减法！",
            "同号相加，异号相减，记住这个口诀。",
            "绝对值大的数决定结果的符号！"
        ],
        code: `加法规则：
(+5) + (+3) = +8  （同号相加）
(-5) + (-3) = -8  （同号相加）
(+5) + (-3) = +2  （异号相减，正数大）
(-5) + (+3) = -2  （异号相减，负数大）`,
        html_content: generateSlideHTML(
            "正数和负数的加减法",
            "➕➖",
            "正数和负数相加时，同号相加，异号相减！绝对值大的数决定结果的符号。",
            [
                { number: "+8", text: "(+5) + (+3) = +8  （同号相加）" },
                { number: "-8", text: "(-5) + (-3) = -8  （同号相加）" },
                { number: "+2", text: "(+5) + (-3) = +2  （异号相减）" }
            ],
            { start: "#FFD93D", end: "#F6B93B", text: "#F6B93B", accent: "#FFD93D" }
        )
    }
];

export default slidesData; // 在单文件环境中，我们只导出一个App组件