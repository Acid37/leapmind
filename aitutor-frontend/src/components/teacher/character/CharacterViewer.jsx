import React from 'react';
import VirtualTeacherViewer from '@/components/virtualTeacher/VirtualTeacherViewer.jsx';
import { sharedViewer } from '@/features/vrmViewer/viewerContext.js';
import {
  DEFAULT_TEACHER_AVATARS,
  getLocalTeacherPreference,
} from '@/services/virtualTeacherService.js';

export default function CharacterViewer() {
  const preference = getLocalTeacherPreference();
  const modelUrl = preference?.modelUrl ?? DEFAULT_TEACHER_AVATARS[2].modelUrl;

  return (
    <div className="absolute inset-0">
      <VirtualTeacherViewer viewer={sharedViewer} modelUrl={modelUrl} />
    </div>
  );
}


