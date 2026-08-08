import { createContext } from "react";
import { Viewer } from "./viewer.js";

// 共享单例 Viewer，避免多实例造成渲染与控制分离
export const sharedViewer = new Viewer();

export const ViewerContext = createContext({ viewer: sharedViewer });