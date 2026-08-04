import React from 'react';
import VrmViewer from '@/components/character/vrmViewer';
import { ViewerContext, sharedViewer } from '@/features/vrmViewer/viewerContext.js';

export default function CharacterViewer() {
  const viewer = sharedViewer;
  return (
    <div className="absolute inset-0">
      <ViewerContext.Provider value={{ viewer }}>
        <VrmViewer />
      </ViewerContext.Provider>
    </div>
  );
}


