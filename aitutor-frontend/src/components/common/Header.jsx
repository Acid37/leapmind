const Header = ({ lessonSubtitle, dark = false, onBack }) => (
	<header className={`flex items-center justify-between p-4 border-b flex-shrink-0 ${dark ? 'border-white/20' : 'border-slate-200'}`}>
		<div className="flex items-center gap-3">
			{onBack ? (
				<button
					type="button"
					onClick={onBack}
					className={`flex items-center px-2 py-1 rounded-md transition-colors ${dark ? 'text-white hover:text-white/80 hover:bg-white/10' : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100'}`}
				>
					<span className="mr-1">←</span>
					<span>返回</span>
				</button>
			) : null}
			<h1 className={`text-lg font-semibold ${dark ? 'text-white' : 'text-slate-900'}`}>在线课堂</h1>
		</div>
		{/* <p className={`text-sm ${dark ? 'text-white/70' : 'text-slate-500'}`}>{lessonSubtitle}</p> */}
	</header>
);

export default Header;
