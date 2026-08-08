import ReactMarkdown from "react-markdown";
import remarkMath from "remark-math";
import rehypeKatex from "rehype-katex";
import "katex/dist/katex.min.css";

export default function MathText({ children }) {
  const content = children === undefined || children === null ? "" : String(children);

  return (
    <ReactMarkdown
      remarkPlugins={[remarkMath]}
      rehypePlugins={[rehypeKatex]}
      skipHtml
      components={{
        p: ({ children: paragraphChildren }) => <span>{paragraphChildren}</span>,
      }}
    >
      {content}
    </ReactMarkdown>
  );
}
