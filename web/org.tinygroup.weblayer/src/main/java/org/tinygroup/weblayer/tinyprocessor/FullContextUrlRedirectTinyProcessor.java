/**
 *  Copyright (c) 1997-2013, www.tinygroup.org (luo_guo@icloud.com).
 *
 *  Licensed under the GPL, Version 3.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.gnu.org/licenses/gpl.html
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.tinygroup.weblayer.tinyprocessor;

import org.tinygroup.commons.tools.ObjectUtil;
import org.tinygroup.fileresolver.FullContextFileRepository;
import org.tinygroup.logger.LogLevel;
import org.tinygroup.logger.Logger;
import org.tinygroup.logger.LoggerFactory;
import org.tinygroup.vfs.FileObject;
import org.tinygroup.weblayer.AbstractTinyProcessor;
import org.tinygroup.weblayer.WebContext;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;

/**
 * 
 * @author renhui
 * 
 */
public class FullContextUrlRedirectTinyProcessor extends AbstractTinyProcessor {

	private static final String CACHE_CONTROL = "max-age=315360000";

	/**
	 * 是否需要检查文件存在
	 */
	private static final String CHECK_FILE_EXISTS = "checkFileExists";

	/**
	 * CHECK_FILE_EXISTS的默认值
	 */
	private static final boolean CHECK_FILE_EXISTS_DEFAULT = false;

	private Boolean checkFileExists;

	private FullContextFileRepository fullContextFileRepository;

	private static final Logger logger = LoggerFactory
			.getLogger(FullContextUrlRedirectTinyProcessor.class);

	
	public FullContextFileRepository getFullContextFileRepository() {
		return fullContextFileRepository;
	}


	public void setFullContextFileRepository(
			FullContextFileRepository fullContextFileRepository) {
		this.fullContextFileRepository = fullContextFileRepository;
	}

	@Override
	protected void customInit() throws ServletException {
		if(checkFileExists == null) {
			checkFileExists = ObjectUtil
					.defaultIfNull(Boolean.parseBoolean(get(CHECK_FILE_EXISTS)), CHECK_FILE_EXISTS_DEFAULT);
		}
	}

	@Override
	public boolean isMatch(String urlString) {
		FileObject fileObject = fullContextFileRepository
				.getFileObject(urlString);
		if(!super.isMatch(urlString)) return false;
		//是否检查资源文件
		return checkFileExists?(fileObject!=null && fileObject.isExist()):true;
	}

	public void reallyProcess(String servletPath, WebContext context) throws ServletException, IOException{
		logger.logMessage(LogLevel.DEBUG, "{}开始处理...", servletPath);
		HttpServletResponse response = context.getResponse();
		HttpServletRequest request = context.getRequest();
		FileObject fileObject = fullContextFileRepository
				.getFileObject(servletPath);
//		try {
			if (fileObject != null && fileObject.isExist()) {
				String ims = request.getHeader("If-Modified-Since");
				if (ims != null && ims.length() > 0) {
					if (ims.equals(new Date(fileObject.getLastModifiedTime())
							.toGMTString())) {
						response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
						return;
					}
				}
				String dateString = new Date(fileObject.getLastModifiedTime())
						.toGMTString();
				response.setStatus(HttpServletResponse.SC_OK);
				response.setHeader("Last-modified", dateString);
				response.setHeader("Connection", "keep-alive");
				response.setHeader("Cache-Control", CACHE_CONTROL);
				response.setHeader("Date", dateString);
				response.setContentType(fullContextFileRepository
						.getFileContentType(fileObject.getExtName()));
				OutputStream outputStream = response.getOutputStream();
				InputStream stream = new BufferedInputStream(
						fileObject.getInputStream());
				byte[] buffer = new byte[stream.available()];
				stream.read(buffer);
				stream.close();
				outputStream.write(buffer);
				outputStream.close();
				logger.logMessage(LogLevel.DEBUG, "{}处理完成。", servletPath);
			} else {
				logger.logMessage(LogLevel.DEBUG, "{}未找到。", servletPath);
				response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			}
//		} catch (IOException e) {
//			logger.errorMessage("{}写入响应信息出现异常", e, servletPath);
//			throw new RuntimeException(e);
//		}

	}

}
