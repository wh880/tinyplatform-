/**
 * Copyright (c) 1997-2013, www.tinygroup.org (luo_guo@icloud.com).
 * <p>
 * Licensed under the GPL, Version 3.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.gnu.org/licenses/gpl.html
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tinygroup.exception;

import org.tinygroup.commons.i18n.LocaleUtil;
import org.tinygroup.commons.tools.ExceptionUtil;
import org.tinygroup.commons.tools.StringUtil;
import org.tinygroup.context.Context;
import org.tinygroup.exception.util.ErrorUtil;
import org.tinygroup.i18n.I18nMessage;
import org.tinygroup.i18n.I18nMessageFactory;

import java.util.List;
import java.util.Locale;

public class BaseCheckedException extends Exception {
    private static final long serialVersionUID = -1141168272047460629L;
    private static final I18nMessage i18nMessage = I18nMessageFactory
            .getI18nMessages();// 需要在启动的时候注入进来
    private String errorMsg;

    private ErrorCode errorCode;

    public BaseCheckedException(String errorCode, Object... params) {
        this(errorCode, "", LocaleUtil.getContext().getLocale(), params);
    }

    public BaseCheckedException(String errorCode, String defaultErrorMsg,
                                Locale locale, Object... params) {
        String errorI18nMsg = i18nMessage.getMessage(errorCode, locale,
                defaultErrorMsg, params);
        initErrorCode(errorCode, errorI18nMsg);
    }

    public BaseCheckedException(String errorCode, Throwable throwable,
                                Object... params) {
        this(errorCode, "", LocaleUtil.getContext().getLocale(), throwable,
                params);
    }


    public BaseCheckedException(String errorCode, String defaultErrorMsg,
                                Throwable throwable, Object... params) {
        this(errorCode, defaultErrorMsg, LocaleUtil.getContext().getLocale(),
                throwable, params);
    }

    public BaseCheckedException(String errorCode, String defaultErrorMsg,
                                Locale locale, Throwable throwable, Object... params) {
        super(throwable);
        String errorI18nMsg = i18nMessage.getMessage(errorCode, locale,
                defaultErrorMsg, params);
        initErrorCode(errorCode, errorI18nMsg);
    }

    public BaseCheckedException(String errorCode, Context context, Locale locale) {
        this(errorCode, "", context, locale);
    }

    public BaseCheckedException(String errorCode, String defaultErrorMsg,
                                Context context, Locale locale) {
        String errorI18nMsg = i18nMessage.getMessage(errorCode, defaultErrorMsg,
                context, locale);
        initErrorCode(errorCode, errorI18nMsg);
    }

    public BaseCheckedException(String errorCode, Context context) {
        this(errorCode, "", context, LocaleUtil.getContext().getLocale());
    }

    public BaseCheckedException() {
        super();
    }

    public BaseCheckedException(Throwable cause) {
        super(cause);
    }

    public static ErrorContext getErrorContext(Throwable throwable) {
        ErrorContext errorContext = new ErrorContext();
        List<Throwable> causes = ExceptionUtil.getCauses(throwable, true);
        for (Throwable cause : causes) {
            if (cause instanceof BaseCheckedException) {
                BaseCheckedException exception = (BaseCheckedException) cause;
                ErrorUtil.makeAndAddError(errorContext,
                        exception.getErrorCode(), exception.getMessage());
            }
        }
        return errorContext;
    }

    @Override
    public String getMessage() {
        StringBuffer msgBuffer = new StringBuffer();
        if (errorCode == null) {
            if (StringUtil.isBlank(errorMsg))
                return super.getMessage();
            msgBuffer.append(errorMsg);
        } else {
            msgBuffer.append(String.format("[%s]", errorCode));
            if (!StringUtil.isBlank(errorMsg)) msgBuffer.append(" : ").append(errorMsg);
        }
        return msgBuffer.toString();
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    private void initErrorCode(String errorCode, String errorI18nMsg) {
        if (StringUtil.isBlank(errorI18nMsg)) {
            this.errorMsg = errorCode;//errorCode获取不到国际化信息，认为传递的errorCode就是错误信息.
        } else {
            this.errorMsg = errorI18nMsg;
        }
        try {
            this.errorCode = ErrorCodeFactory.parseErrorCode(errorCode, this);
        } catch (Exception e) {//兼容以前错误码没有规范的处理,扑捉异常不外抛
            this.errorCode = null;
        }
    }

    /**
     * Check whether this exception contains an exception of the given type:
     * either it is of the given class itself or it contains a nested cause of
     * the given type.
     *
     * @param exType the exception type to look for
     * @return whether there is a nested exception of the specified type
     */
    public boolean contains(Class exType) {
        if (exType == null) {
            return false;
        }
        if (exType.isInstance(this)) {
            return true;
        }
        Throwable cause = getCause();
        if (cause == this) {
            return false;
        }
        if (cause instanceof BaseCheckedException) {
            return ((BaseCheckedException) cause).contains(exType);
        } else {
            while (cause != null) {
                if (exType.isInstance(cause)) {
                    return true;
                }
                if (cause.getCause() == cause) {
                    break;
                }
                cause = cause.getCause();
            }
            return false;
        }
    }

}
