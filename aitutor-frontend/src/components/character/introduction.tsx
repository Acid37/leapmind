import { useState, useCallback } from "react";
import { Link } from "./link";

type Props = {
  openAiKey: string;
  koeiroMapKey: string;
  onChangeAiKey: (openAiKey: string) => void;
  onChangeKoeiromapKey: (koeiromapKey: string) => void;
};
export const Introduction = ({
  openAiKey,
  koeiroMapKey,
  onChangeAiKey,
  onChangeKoeiromapKey,
}: Props) => {
  const [opened, setOpened] = useState(true);

  const handleAiKeyChange = useCallback(
    (event: React.ChangeEvent<HTMLInputElement>) => {
      onChangeAiKey(event.target.value);
    },
    [onChangeAiKey]
  );

  const handleKoeiromapKeyChange = useCallback(
    (event: React.ChangeEvent<HTMLInputElement>) => {
      onChangeKoeiromapKey(event.target.value);
    },
    [onChangeKoeiromapKey]
  );

  return opened ? (
    <div className="absolute z-40 w-full h-full px-24 py-40  bg-black/30 font-M_PLUS_2">
      <div className="mx-auto my-auto max-w-3xl max-h-full p-24 overflow-auto bg-white rounded-16">
        <div className="my-24">
          <div className="my-8 font-bold typography-20 text-secondary ">
            关于本应用
          </div>
          <div>
            仅使用Web浏览器即可与3D角色进行对话，支持麦克风、文本输入和语音合成。还可以更换角色（VRM）、设置性格和调整声音。
          </div>
        </div>
        <div className="my-24">
          <div className="my-8 font-bold typography-20 text-secondary">
            技术介绍
          </div>
          <div>
            3D模型的显示和操作使用
            <Link
              url={"https://github.com/pixiv/three-vrm"}
              label={"@pixiv/three-vrm"}
            />
            ，对话文本生成使用
            <Link
              url={
                "https://openai.com/blog/introducing-chatgpt-and-whisper-apis"
              }
              label={"ChatGPT API"}
            />
            ，语音合成使用
            <Link url={"https://console.volcengine.com/"} label={"火山引擎"} />
            的
            <Link
              url={
                "https://console.volcengine.com/speech"
              }
              label={"语音合成TTS"}
            />
            。详细信息请查看
            <Link
              url={"https://inside.pixiv.blog/2023/04/28/160000"}
              label={"技术解说文章"}
            />
            。
          </div>
          <div className="my-16">
            此演示在GitHub上公开了源代码。请自由尝试修改和改进！
            <br />
            代码仓库：
            <Link
              url={"https://github.com/pixiv/ChatVRM"}
              label={"https://github.com/pixiv/ChatVRM"}
            />
          </div>
        </div>

        <div className="my-24">
          <div className="my-8 font-bold typography-20 text-secondary">
            使用注意事项
          </div>
          <div>
            请不要故意引导歧视性或暴力性言论，或贬低特定人物的言论。另外，使用VRM模型更换角色时请遵守模型的使用条件。
          </div>
        </div>

        <div className="my-24">
          <div className="my-8 font-bold typography-20 text-secondary">
            火山引擎API密钥
          </div>
          <input
            type="text"
            placeholder="XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
            value={koeiroMapKey}
            onChange={handleKoeiromapKeyChange}
            className="my-4 px-16 py-8 w-full h-40 bg-surface3 hover:bg-surface3-hover rounded-4 text-ellipsis"
          ></input>
          <div>
            请从火山引擎控制台获取API密钥。
            <Link
              url="https://console.volcengine.com/speech"
              label="详细信息"
            />
          </div>
        </div>
        <div className="my-24">
          <div className="my-8 font-bold typography-20 text-secondary">
            OpenAI API密钥
          </div>
          <input
            type="text"
            placeholder="sk-..."
            value={openAiKey}
            onChange={handleAiKeyChange}
            className="my-4 px-16 py-8 w-full h-40 bg-surface3 hover:bg-surface3-hover rounded-4 text-ellipsis"
          ></input>
          <div>
            API密钥可以从
            <Link
              url="https://platform.openai.com/account/api-keys"
              label="OpenAI官网"
            />
            获取。请将获取的API密钥输入到表单中。
          </div>
          <div className="my-16">
            ChatGPT
            API直接从浏览器访问。另外，API密钥和对话内容不会保存在服务器上。
            <br />
            ※使用的模型是ChatGPT API (GPT-3.5)。
          </div>
        </div>
        <div className="my-24">
          <button
            onClick={() => {
              setOpened(false);
            }}
            className="font-bold bg-secondary hover:bg-secondary-hover active:bg-secondary-press disabled:bg-secondary-disabled text-white px-24 py-8 rounded-oval"
          >
            输入API密钥开始
          </button>
        </div>
      </div>
    </div>
  ) : null;
};
