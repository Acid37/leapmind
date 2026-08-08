const Navigation = ({ onPrev, onNext, onPlay, totalSlides, currentIndex }) => (
    <div className="flex flex-col items-center justify-center mt-4 space-y-3">
        <div className="flex items-center space-x-2.5">
            {Array.from({ length: totalSlides }).map((_, index) => (
                <span key={index} className={`w-2 h-2 rounded-full ${index === currentIndex ? 'bg-blue-500' : 'bg-slate-300'} transition-all`}></span>
            ))}
        </div>
        <div className="flex items-center space-x-6">
            <button onClick={onPrev} className="interactive-btn text-white font-medium flex items-center px-4 py-2 rounded-lg bg-purple-400/40 hover:bg-purple-400/60 transition-colors backdrop-blur-sm">
                <svg className="w-5 h-5 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M15 19l-7-7 7-7"></path></svg>
                上一页
            </button>
            <button onClick={onPlay} id="playBtn" className="interactive-btn text-white bg-blue-500 hover:bg-blue-600 font-medium flex items-center px-5 py-2.5 rounded-lg shadow-soft transition-colors">
                <svg className="w-5 h-5 mr-2" fill="currentColor" viewBox="0 0 20 20"><path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM9.555 7.168A1 1 0 008 8v4a1 1 0 001.555.832l3-2a1 1 0 000-1.664l-3-2z" clipRule="evenodd"></path></svg>
                播放
            </button>
            <button onClick={onNext} className="interactive-btn text-white font-medium flex items-center px-4 py-2 rounded-lg bg-purple-400/40 hover:bg-purple-400/60 transition-colors backdrop-blur-sm">
                下一页
                <svg className="w-5 h-5 ml-1" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 5l7 7-7 7"></path></svg>
            </button>
        </div>
    </div>
);

export default Navigation;