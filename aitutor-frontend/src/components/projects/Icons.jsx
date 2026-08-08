import React from 'react';

export const IconRocket = () => (
  <svg xmlns="http://www.w3.org/2000/svg" className="h-20 w-20 -rotate-45" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="1.5">
    <path strokeLinecap="round" strokeLinejoin="round" d="M13 10V3L4 14h7v7l9-11h-7z" />
  </svg>
);

export const IconApple = () => (
  <svg xmlns="http://www.w3.org/2000/svg" className="h-16 w-16" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="1.5">
    <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 12.5a3.5 3.5 0 01-3.5 3.5h-8a3.5 3.5 0 010-7h.5" />
    <path strokeLinecap="round" strokeLinejoin="round" d="M12 6V4m0 2a2 2 0 100 4m0-4a2 2 0 110 4" />
  </svg>
);

export const IconEnergy = () => (
  <svg xmlns="http://www.w3.org/2000/svg" className="h-16 w-16" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
    <path strokeLinecap="round" strokeLinejoin="round" d="M13 10V3L4 14h7v7l9-11h-7z" />
  </svg>
);

export const IconTrophy = () => (
  <svg xmlns="http://www.w3.org/2000/svg" className="h-16 w-16" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
    <path strokeLinecap="round" strokeLinejoin="round" d="M9 11l3-3m0 0l3 3m-3-3v8m0-13a9 9 0 110 18 9 9 0 010-18z" />
  </svg>
);

export const IconAtom = () => (
  <svg xmlns="http://www.w3.org/2000/svg" className="h-16 w-16" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="1.5">
    <path strokeLinecap="round" strokeLinejoin="round" d="M12 21a9 9 0 100-18 9 9 0 000 18z" />
    <path strokeLinecap="round" strokeLinejoin="round" d="M19.071 4.929A9.008 9.008 0 004.929 19.071" />
    <path strokeLinecap="round" strokeLinejoin="round" d="M4.929 4.929A9.008 9.008 0 0119.071 19.071" />
  </svg>
);

export const ICONS = [IconRocket, IconApple, IconEnergy, IconAtom, IconTrophy];

export const COLORS = [
  { bg: 'bg-green-400', text: 'text-green-800', border: 'border-green-500', connector: '#a3e635' },
  { bg: 'bg-sky-400', text: 'text-sky-800', border: 'border-sky-500', connector: '#38bdf8' },
  { bg: 'bg-violet-400', text: 'text-violet-800', border: 'border-violet-500', connector: '#a78bfa' },
  { bg: 'bg-rose-400', text: 'text-rose-800', border: 'border-rose-500', connector: '#fb7185' },
  { bg: 'bg-amber-400', text: 'text-amber-800', border: 'border-amber-500', connector: '#facc15' },
];


