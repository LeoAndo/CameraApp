# 要件
 アプリ内で写真撮影ができる
 撮影した写真をスマホに保存できる

# アーキテクチャ
- カメラ実装にはcameraXライブラリを利用する
- 画面はAndroid View Systemを採用する
- ViewModelは利用しない
- ロジックは全てMainActivity.ktに集約する
- レイアウトは`androidx.constraintlayout.widget.ConstraintLayout`ではなく`LinearLayout`を利用すること

# ソースコードコメント
- KotlinのKDoc形式に従い、IDEのドキュメント機能で表示できる形式で記述すること
- できるだけ細かい粒度で記述すること

# 画面イメージ
sample.pngの画像ファイルを参考に画面作成してください。