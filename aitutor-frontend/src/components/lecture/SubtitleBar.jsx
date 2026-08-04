const SubtitleBar = ({ text, height = 56 }) => {
    console.log('[调试] SubtitleBar 渲染，接收到的 text:', text);
    return(
    <div
        className="w-full bg-slate-100 rounded-lg mt-2 flex items-center justify-center px-3 py-2 shadow-inner"
        style={{ height }}
    >
        <p className="text-slate-600 font-medium text-base text-center" dangerouslySetInnerHTML={{ __html: text }} />
    </div>
    );
};
export default SubtitleBar;