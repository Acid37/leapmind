import React from 'react';

const LevelNode = ({ level, isLast, onSelect, isSelected }) => {
  const { title, status, icon: Icon, color } = level;

  const statusStyles = {
    completed: {
      icon: `${color.bg} ${color.text} ${color.border}`,
      text: 'text-green-700',
      title: 'text-slate-800',
      connector: color.connector,
    },
    active: {
      icon: `bg-orange-400 text-white border-orange-500 animate-pulse`,
      text: 'text-orange-600',
      title: 'text-orange-600 font-black',
      connector: 'bg-orange-400',
    },
    locked: {
      icon: 'bg-slate-200 text-slate-400 border-slate-300',
      text: 'text-slate-400',
      title: 'text-slate-400',
      connector: 'bg-slate-300',
    }
  };

  const currentStyle = statusStyles[status] || statusStyles.locked;

  return (
    <div className="relative flex flex-col items-center">
      <div className={`w-32 h-32 rounded-full border-8 shadow-lg flex items-center justify-center transition-all duration-300 ${currentStyle.icon} ${isSelected ? 'ring-4 ring-blue-400' : ''}`}
           style={{ filter: 'drop-shadow(0 10px 10px rgba(0,0,0,0.06))' }}>
        <Icon />
      </div>

      <div className="text-center mt-4">
        <h2 className={`text-xl font-extrabold ${currentStyle.title}`}
            style={{ textShadow: '0 2px 0 rgba(0,0,0,0.04)' }}>{title}</h2>
        {status === 'completed' && <p className={`text-sm font-bold ${currentStyle.text}`}>已通关!</p>}
        {status === 'active' && (
          <button onClick={onSelect} className="mt-3 bg-orange-500 text-white font-bold py-2 px-8 rounded-full text-lg shadow-md hover:bg-orange-600 transition-all uppercase tracking-wider transform hover:scale-105">
            进入
          </button>
        )}
        {status === 'locked' && <p className={`text-sm font-bold ${currentStyle.text}`}>等待解锁</p>}
        {status === undefined && (
          <button onClick={onSelect} className="mt-3 bg-blue-600 text-white font-bold py-2 px-8 rounded-full text-lg shadow-md hover:bg-blue-700 transition-all uppercase tracking-wider transform hover:scale-105">
            进入
          </button>
        )}
      </div>

      {!isLast && (
        <div
          className="absolute top-full left-1/2 -translate-x-1/2 w-1.5 h-16"
          style={{
            backgroundImage: `linear-gradient(to bottom, ${currentStyle.connector} 50%, transparent 50%)`,
            backgroundSize: '100% 12px',
          }}
        ></div>
      )}
    </div>
  );
};

export default LevelNode;


