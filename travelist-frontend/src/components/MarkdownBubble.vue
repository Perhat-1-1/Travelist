<script setup>
import { computed } from 'vue'
import { Marked } from 'marked'
import { markedHighlight } from 'marked-highlight'
import hljs from 'highlight.js/lib/common'
import DOMPurify from 'dompurify'
import 'highlight.js/styles/atom-one-light.css'

const props = defineProps({
  text: { type: String, default: '' },
})

// marked + 代码高亮插件:高亮失败时按纯文本渲染
const marked = new Marked(
  markedHighlight({
    langPrefix: 'hljs language-',
    highlight(code, lang) {
      try {
        const language = hljs.getLanguage(lang) ? lang : 'plaintext'
        return hljs.highlight(code, { language }).value
      } catch {
        return code
      }
    },
  }),
)

// LLM 输出不可信:先转 HTML,再消毒(防 XSS),最后 v-html
const html = computed(() => DOMPurify.sanitize(marked.parse(props.text)))
</script>

<template>
  <div class="markdown-body" v-html="html" />
</template>

<style>
/* v-html 内容不在 scoped 作用域内,使用全局样式 */
.markdown-body {
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}

.markdown-body > :first-child {
  margin-top: 0;
}

.markdown-body > :last-child {
  margin-bottom: 0;
}

.markdown-body p,
.markdown-body ul,
.markdown-body ol,
.markdown-body blockquote,
.markdown-body pre,
.markdown-body table {
  margin: 0 0 10px;
}

.markdown-body h1,
.markdown-body h2,
.markdown-body h3,
.markdown-body h4 {
  margin: 14px 0 8px;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.35;
}

.markdown-body h1 {
  font-size: 18px;
}

.markdown-body h2 {
  font-size: 17px;
}

.markdown-body ul,
.markdown-body ol {
  padding-left: 20px;
}

.markdown-body li {
  margin: 2px 0;
}

.markdown-body blockquote {
  margin: 0 0 10px;
  padding: 6px 10px;
  border-left: 3px solid #1989fa;
  background: #f7f8fa;
  color: #646566;
}

.markdown-body code {
  display: inline-block;
  padding: 1px 5px;
  border-radius: 4px;
  background: #f2f3f5;
  font-family: ui-monospace, Consolas, monospace;
  font-size: 12.5px;
}

.markdown-body pre {
  padding: 10px 12px;
  border-radius: 8px;
  background: #fcfcff;
  border: 1px solid #ebedf0;
  overflow-x: auto;
}

.markdown-body pre code {
  display: block;
  padding: 0;
  background: none;
  font-size: 12.5px;
  line-height: 1.6;
}

.markdown-body table {
  display: block;
  overflow-x: auto;
  border-collapse: collapse;
}

.markdown-body th,
.markdown-body td {
  padding: 6px 10px;
  border: 1px solid #ebedf0;
  text-align: left;
  white-space: nowrap;
}

.markdown-body th {
  background: #f7f8fa;
}

.markdown-body a {
  color: #1989fa;
  text-decoration: none;
}

.markdown-body hr {
  margin: 12px 0;
  border: none;
  border-top: 1px solid #ebedf0;
}

/* 自定义语言被识别为纯文本时的占位小字 */
.markdown-body .hljs {
  background: #fcfcff;
}
</style>
