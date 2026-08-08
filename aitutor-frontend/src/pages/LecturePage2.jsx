import React from 'react';
import Header from '../components/common/Header';
import SlideViewer from '../components/lecture/SlideViewer';
import TeacherPanel from '../components/teacher/TeacherPanel';

const LecturePage2 = ({ projectId, courseId: courseIdProp, onBack }) => {
	const courseId = courseIdProp ?? projectId;
	return (
		<div className="w-full flex h-full bg-gradient-to-br from-purple-700 via-purple-600 via-blue-600 via-blue-700 to-blue-900" style={{backgroundImage: "linear-gradient(135deg, #861FCE 0%, #861FCE 16%, #731CCD 16%, #731CCD 32%, #6B1CCF 32%, #6B1CCF 48%, #631DCE 48%, #631DCE 64%, #5A1BCE 64%, #5A1BCE 80%,rgb(86, 43, 205) 80%,rgb(47, 8, 154) 100%)"}}>
			<div className="w-3/4 flex flex-col overflow-hidden">
				<div className="bg-white/10 backdrop-blur-md border-b border-white/20">
					<Header lessonSubtitle={courseId ? `课程 ${courseId}` : '在线课堂'} dark={true} onBack={onBack} />
				</div>
				<div className="flex-1 overflow-hidden">
					<SlideViewer courseId={courseId} projectId={projectId} />
				</div>
			</div>
			<TeacherPanel dark={true} />
		</div>
	);
}

export default LecturePage2;