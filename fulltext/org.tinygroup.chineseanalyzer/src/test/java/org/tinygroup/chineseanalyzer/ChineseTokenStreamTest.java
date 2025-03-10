package org.tinygroup.chineseanalyzer;

import java.io.IOException;
import java.io.StringReader;

import junit.framework.TestCase;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.tinygroup.tinyrunner.Runner;

/**
 * TinySeg的中文令牌流测试用例
 * @author yancheng11334
 *
 */
public class ChineseTokenStreamTest extends TestCase{

	protected void setUp() throws Exception {
		super.setUp();
		Runner.init("application.xml", null);
	}
	
	public void testChineseToken() {
		ChineseTokenStream token = new ChineseTokenStream(
				new StringReader(
				// "中华人民共和国"));
						"Hello回家的感觉真好本报驻日本记者于青欢聚使馆喜迎新春留日学子年年如此月日近名中国留学人员前来出席中国驻日大使馆举办的春节招待会兴高采烈地送牛迎虎使馆临时代办武大伟向大家拜年教育参赞曲德林向大家慰问祝酒使馆大厅响起阵阵掌声充满欢声笑语辞旧迎新之际是抚今追昔之时回首牛年令人心潮难平难忘庆祝香港回归的不眠之夜中共十五大举世瞩目有人说起去年是毛主席接见留苏学生并发表希望寄托在你们身上著名演说周年对海外学子来说是值得纪念的一年又有人说起去年是中日邦交正常化周年对留日学子来说又多了一分喜庆的一年还有人说起去年是祖国农业大丰收的一年蔬菜水果物美价廉辞旧迎新之际也是展望未来之时大家你一言我一语议论着今年是中日和平友好条约缔结周年江泽民主席年内将来日访问这是中国国家元首首次访问日本衷心希望中日关系稳定健康不断发展今年是中国实行改革开放政策周年国家继续贯彻支持留学鼓励回国来去自由的方针海外学子应该为科教兴国刻苦学习多做实事贡献力量海外学子聚会也是交流信息的机会那位刚从国内回来的学生说现在国内收视率最高的是电视连续剧水浒传最流行的歌词是路见不平一声吼该出手时就出手还听说一位搞摄影的在台湾举行的作品展圆满成功另一位搞摄影的将在维也纳举办作品展这位从使馆教育处获悉去年从日本回国的留学人员总计人其中博士生人硕士生人另一位说一些在日就职的中国留学人员正在酝酿成立社会团体制订为国服务规划海外学子聚会也是抒发思乡爱国情怀的时候他们唱歌时都是那么投入那么动情草原之夜长江之歌在希望的田野上让人怀念故乡达坂城的姑娘拉骆驼的黑小伙父老乡亲令人思念家人我和我的祖国我爱你中国使人爱国之情在胸中激荡到使馆就是到家了看着餐桌上的春卷麻团水饺回家的感觉真好一位留学生自言自语地说道本报东京月日电"));
		try {
			while (token.incrementToken()) {
				System.out.println(token.getAttribute(CharTermAttribute.class));
			}
		} catch (IOException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
	
	public void testAnalyzer() throws IOException{
		Analyzer analyzer = new ChineseAnalyzer();
		
		System.out.println("------------------------");
		String text = "中国十五大辞旧迎新\n几天";
		TokenStream tokenStream = analyzer.tokenStream("content", new StringReader(text));   
        tokenStream.addAttribute(CharTermAttribute.class);   

        try {
			while (tokenStream.incrementToken()) {
				System.out.println(tokenStream.getAttribute(CharTermAttribute.class));
			}
		} catch (IOException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}  
	}
}
