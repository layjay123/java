package com.jsoniter;

import com.jsoniter.any.*;

import java.io.IOException;

class IterImplSkip {

    private static final boolean[] breaks = new boolean[256];

    static {
        breaks[' '] = true;
        breaks['\t'] = true;
        breaks['\n'] = true;
        breaks['\r'] = true;
        breaks[','] = true;
        breaks['}'] = true;
        breaks[']'] = true;
    }

    public static final LazyAny readAny(JsonIterator iter) throws IOException {
        int start = iter.head;
        byte c = iter.nextToken();
        switch (c) {
            case '"':
                skipString(iter);
                return new StringLazyAny(iter.buf, start, iter.head);
            case '-':
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
                skipUntilBreak(iter);
                return new NumberLazyAny(iter.buf, start, iter.head);
            case 't':
            case 'f':
                skipUntilBreak(iter);
                return new BooleanLazyAny(iter.buf, start, iter.head);
            case 'n':
                skipUntilBreak(iter);
                return new NullLazyAny(iter.buf, start, iter.head);
            case '[':
                skipArray(iter);
                return new ArrayLazyAny(iter.buf, start, iter.head);
            case '{':
                skipObject(iter);
                return new ObjectLazyAny(iter.buf, start, iter.head);
            default:
                throw iter.reportError("IterImplSkip", "do not know how to skip: " + c);
        }
    }

    public static final void skip(JsonIterator iter) throws IOException {
        byte c = iter.nextToken();
        switch (c) {
            case '"':
                skipString(iter);
                return;
            case '-':
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
            case 't':
            case 'f':
            case 'n':
                skipUntilBreak(iter);
                return;
            case '[':
                skipArray(iter);
                return;
            case '{':
                skipObject(iter);
                return;
            default:
                throw iter.reportError("IterImplSkip", "do not know how to skip: " + c);
        }
    }

    final static void skipObject(JsonIterator iter) throws IOException {
        int level = 1;
        for (; ; ) {
            for (int i = iter.head; i < iter.tail; i++) {
                switch (iter.buf[i]) {
                    case '"': // If inside string, skip it
                        iter.head = i + 1;
                        skipString(iter);
                        i = iter.head - 1; // it will be i++ soon
                        break;
                    case '{': // If open symbol, increase level
                        level++;
                        break;
                    case '}': // If close symbol, increase level
                        level--;

                        // If we have returned to the original level, we're done
                        if (level == 0) {
                            iter.head = i + 1;
                            return;
                        }
                        break;
                }
            }
            if (!iter.loadMore()) {
                return;
            }
        }
    }

    final static void skipArray(JsonIterator iter) throws IOException {
        int level = 1;
        for (; ; ) {
            for (int i = iter.head; i < iter.tail; i++) {
                switch (iter.buf[i]) {
                    case '"': // If inside string, skip it
                        iter.head = i + 1;
                        skipString(iter);
                        i = iter.head - 1; // it will be i++ soon
                        break;
                    case '[': // If open symbol, increase level
                        level++;
                        break;
                    case ']': // If close symbol, increase level
                        level--;

                        // If we have returned to the original level, we're done
                        if (level == 0) {
                            iter.head = i + 1;
                            return;
                        }
                        break;
                }
            }
            if (!iter.loadMore()) {
                return;
            }
        }
    }

    final static void skipUntilBreak(JsonIterator iter) throws IOException {
        // true, false, null, number
        for (; ; ) {
            for (int i = iter.head; i < iter.tail; i++) {
                byte c = iter.buf[i];
                if (breaks[c]) {
                    iter.head = i;
                    return;
                }
            }
            if (!iter.loadMore()) {
                iter.head = iter.tail;
                return;
            }
        }
    }

    final static void skipString(JsonIterator iter) throws IOException {
        for (; ; ) {
            int end = findStringEnd(iter);
            if (end == -1) {
                int j = iter.tail - 1;
                boolean escaped = true;
                for (; ; ) {
                    if (j < iter.head || iter.buf[j] != '\\') {
                        // even number of backslashes
                        // either end of buffer, or " found
                        escaped = false;
                        break;
                    }
                    j--;
                    if (j < iter.head || iter.buf[j] != '\\') {
                        // odd number of backslashes
                        // it is \" or \\\"
                        break;
                    }
                    j--;

                }
                if (!iter.loadMore()) {
                    return;
                }
                if (escaped) {
                    iter.head = 1; // skip the first char as last char readAny is \
                }
            } else {
                iter.head = end;
                return;
            }
        }
    }

    // adapted from: https://github.com/buger/jsonparser/blob/master/parser.go
    // Tries to find the end of string
    // Support if string contains escaped quote symbols.
    final static int findStringEnd(JsonIterator iter) {
        boolean escaped = false;
        for (int i = iter.head; i < iter.tail; i++) {
            byte c = iter.buf[i];
            if (c == '"') {
                if (!escaped) {
                    return i + 1;
                } else {
                    int j = i - 1;
                    for (; ; ) {
                        if (j < iter.head || iter.buf[j] != '\\') {
                            // even number of backslashes
                            // either end of buffer, or " found
                            return i + 1;
                        }
                        j--;
                        if (j < iter.head || iter.buf[j] != '\\') {
                            // odd number of backslashes
                            // it is \" or \\\"
                            break;
                        }
                        j--;
                    }
                }
            } else if (c == '\\') {
                escaped = true;
            }
        }
        return -1;
    }
}
