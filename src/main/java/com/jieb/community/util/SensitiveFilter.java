package com.jieb.community.util;

import org.apache.commons.lang3.CharUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

@Component
public class SensitiveFilter {

    private static final Logger logger = LoggerFactory.getLogger(SensitiveFilter.class);

    private static final String REPLACEMENT = "***";

    private TrieNode root = new TrieNode();

    @PostConstruct
    public void init() {
        try {
            InputStream is = this.getClass().getClassLoader().getResourceAsStream("sensitive_word.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String keyword;
            while ((keyword = reader.readLine()) != null) {
                this.addKeyword(keyword);
            }
        } catch (IOException e) {
            logger.error("加载敏感词文件失败！" + e.getMessage());
        }
    }

    private void addKeyword(String keyword) {
        TrieNode tempNode = root;
        for (int i = 0; i < keyword.length(); i++) {
            char c = keyword.charAt(i);
            TrieNode subNode = tempNode.getSubNode(c);
            if (subNode == null) {
                subNode = new TrieNode();
                tempNode.addSubNode(c, subNode);
            }
            tempNode = subNode;
            if (i == keyword.length() - 1)
                tempNode.setKeywordEnd(true);
        }
    }

    public String filter(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        TrieNode tempNode = root;
        int begin = 0;
        int position = 0;
        StringBuilder sb = new StringBuilder();
        while (position < text.length()) {
            char c = text.charAt(position);
            if (isSymbol(c)) {
                if (tempNode == root) {
                    sb.append(c);
                    begin++;
                }
                position++;
                continue;
            }
            tempNode = tempNode.getSubNode(c);
            if (tempNode == null) {
                sb.append(text.charAt(begin));
                begin++;
                position = begin;
                tempNode = root;
            } else if (tempNode.isKeywordEnd()) {
                sb.append(REPLACEMENT);
                position++;
                begin = position;
                tempNode = root;
            } else {
                position++;
            }
        }
        sb.append(text.substring(begin));

        return sb.toString();
    }

    private boolean isSymbol(Character c) {
        // 0x2E80 - 0x9FFF 是东亚文字
        return !CharUtils.isAsciiAlphanumeric(c) && (c < 0x2E80 || c > 0x9FFF);

    }

    private class TrieNode {
        private boolean isEnd = false;

        private Map<Character, TrieNode> subNodes = new HashMap<>();

        public boolean isKeywordEnd() {
            return isEnd;
        }

        public void setKeywordEnd(boolean keywordEnd) {
            isEnd = keywordEnd;
        }

        public void addSubNode(Character c, TrieNode node) {
            subNodes.put(c, node);
        }

        public TrieNode getSubNode(Character c) {
            return subNodes.get(c);
        }
    }
}

