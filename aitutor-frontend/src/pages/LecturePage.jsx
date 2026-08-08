// 实际项目中，这里会导入各个子组件。为了简化，我们暂时将它们都放在一个文件里。
import React from 'react';
import Header from '../components/common/Header';
import SlideViewer from '../components/lecture/SlideViewer';
import TeacherPanel from '../components/teacher/TeacherPanel';

const LecturePage = ({ projectId, courseId: courseIdProp }) => {
    const courseId = courseIdProp ?? projectId;
    return (
        <div className="w-full flex h-full">
             <div className="w-3/4 flex flex-col bg-white overflow-hidden">
                  <Header lessonSubtitle={courseId ? `课程 ${courseId}` : '在线课堂'} />
                  <SlideViewer courseId={courseId} projectId={projectId} />
             </div>
            <TeacherPanel />
        </div>
    );
}

export default LecturePage;