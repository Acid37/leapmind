const SlideCard = ({ slide }) => (
    <div className="relative w-full aspect-video bg-gradient-to-r from-blue-500 to-teal-400 rounded-2xl shadow-soft-strong text-white overflow-hidden">
        <div className="w-full h-full p-8 flex flex-col md:flex-row">
            <div className="w-1/2 pr-8 flex flex-col justify-center animate-fade-in">
                <h3 className="text-2xl lg:text-4xl font-bold mb-4">{slide.title}</h3>
                <p className="text-sm lg:text-base mb-6 opacity-80">{slide.description}</p>
                <div className="flex flex-wrap gap-2">
                    {slide.tags.map(tag => <span key={tag} className="bg-white/20 text-white text-xs lg:text-sm font-medium px-3 py-1 rounded-full">{tag}</span>)}
                </div>
            </div>
            <div className="w-1/2 mt-6 md:mt-0 bg-slate-800/70 rounded-lg p-2 lg:p-4 shadow-inner-soft overflow-hidden">
                <div className="flex items-center justify-between mb-2 text-xs text-slate-400"><span>code.js</span></div>
                <pre className="text-xs lg:text-sm"><code className="language-js" dangerouslySetInnerHTML={{ __html: slide.code.replace(/\n/g, '<br/>') }} />
                </pre>
            </div>
        </div>
    </div>
);

export default SlideCard;