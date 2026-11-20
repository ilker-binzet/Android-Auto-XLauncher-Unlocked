/* Browser QA for the standalone preview, not an Android Auto/device test. */
const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const http=require('node:http');

(async()=>{
 const output=process.env.CAR_UI_PREVIEW_DIR||path.join(__dirname,'screenshots');
 fs.mkdirSync(output,{recursive:true});
 const mime={'.html':'text/html; charset=utf-8','.css':'text/css','.js':'text/javascript','.svg':'image/svg+xml','.ttf':'font/ttf'};
 const server=http.createServer((req,res)=>{
  let name;try{name=decodeURIComponent(new URL(req.url,'http://localhost').pathname);}catch{res.writeHead(400);res.end();return;}
  const file=path.resolve(__dirname,'.'+(name==='/'?'/index.html':name));
  if(!file.startsWith(__dirname+path.sep)){res.writeHead(403);res.end();return;}
  fs.readFile(file,(err,data)=>{if(err){res.writeHead(404);res.end();return;}res.writeHead(200,{'Content-Type':mime[path.extname(file)]||'application/octet-stream'});res.end(data);});
 });
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||'/usr/bin/chromium',headless:true,args:['--no-sandbox']});
 const context=await browser.newContext({viewport:{width:1024,height:640},colorScheme:'dark',reducedMotion:'reduce'});
 const page=await context.newPage();const errors=[];
 page.on('pageerror',e=>errors.push(e.message));page.on('requestfailed',r=>errors.push(r.url()+': '+r.failure()?.errorText));
 const rows=[];
 try{
  await page.goto(`http://127.0.0.1:${server.address().port}/`);
  await page.evaluate(()=>document.fonts.ready);
  async function screen(name){await page.locator('#car').screenshot({path:path.join(output,name+'.png')});}
  async function geometry(label){
   const result=await page.evaluate(()=>{
    const main=document.querySelector('#car').getBoundingClientRect();
    const buttons=[...document.querySelectorAll('.screen:not([hidden]) button:not(:disabled)')].filter(b=>b.getClientRects().length);
    return{overflow:document.documentElement.scrollWidth>innerWidth, main:{x:main.x,y:main.y,width:main.width,height:main.height}, buttons:buttons.map(b=>{const r=b.getBoundingClientRect();return{name:b.getAttribute('aria-label')||b.textContent.trim(),x:r.x,y:r.y,w:r.width,h:r.height};})};
   });
   assert(!result.overflow,label+': horizontal document overflow');
   for(const b of result.buttons){
    assert(b.w>=75&&b.h>=75,`${label}: target too small: ${b.name} ${b.w}x${b.h}`);
    assert(b.x>=result.main.x-1&&b.x+b.w<=result.main.x+result.main.width+1,`${label}: clipped horizontally ${b.name}`);
    assert(b.y>=result.main.y-1&&b.y+b.h<=result.main.y+result.main.height+1,`${label}: clipped vertically ${b.name}`);
   }
   return result;
  }
  for(const size of [{width:1024,height:640},{width:800,height:520},{width:720,height:400},{width:1280,height:760},{width:360,height:840}]){
   await page.setViewportSize(size);await page.waitForTimeout(120);
   const geometryResult=await geometry(`${size.width}x${size.height}`);
   rows.push({check:'menu geometry',size,buttons:geometryResult.buttons.length,passed:true});
   await screen(`menu-dark-${size.width}x${size.height-40}`);
  }
  await page.setViewportSize({width:1024,height:640});
  await page.locator('#recent-apps').click();assert(await page.locator('#empty').isVisible());
  await page.locator('#show-all').click();assert.equal(await page.locator('.app-tile').count(),6);
  await page.locator('#next').click();assert.equal(await page.locator('.app-tile').count(),3);
  assert((await page.locator('[data-app=long]').textContent()).includes('Çok uzun'));
  await screen('menu-second-page');await page.locator('#previous').click();
  await page.locator('[data-app=youtube]').focus();await page.keyboard.press('ArrowRight');
  assert.equal(await page.evaluate(()=>document.activeElement.dataset.app),'spotify');
  await page.keyboard.press('Enter');assert(await page.locator('#content').isVisible());
  await page.waitForTimeout(120);await screen('content-fit-dark');
  async function aspect(){return await page.locator('#sample-content').evaluate(el=>{const r=el.getBoundingClientRect();return r.width/r.height;});}
  assert(Math.abs(await aspect()-16/9)<.01);
  for(const size of [{width:800,height:520},{width:720,height:400},{width:1280,height:760}]){
   await page.setViewportSize(size);await page.waitForTimeout(100);
   await geometry(`content ${size.width}x${size.height}`);
   assert(Math.abs(await aspect()-16/9)<.01);
   rows.push({check:'content controls and aspect ratio',size,passed:true});
   await screen(`content-dark-${size.width}x${size.height-40}`);
  }
  await page.setViewportSize({width:1024,height:640});await page.waitForTimeout(100);
  await page.locator('#fit-control').click();await page.locator('[data-fit=fill]').click();
  assert.equal(await page.locator('#content-area').getAttribute('data-fit'),'fill');
  assert(Math.abs(await aspect()-16/9)<.01);
  await screen('content-fill-dark');
  await page.locator('#fit-control').click();await screen('display-options');await page.keyboard.press('Escape');
  await page.locator('#content .end-sharing').click();await page.locator('#cancel-stop').click();assert(await page.locator('#content').isVisible());
  await page.keyboard.press('Escape');assert(await page.locator('#launcher').isVisible());
  assert.equal(await page.evaluate(()=>document.activeElement.dataset.app),'spotify');
  await page.locator('#recent-apps').click();assert.equal(await page.locator('.app-tile').count(),1);
  await page.locator('#all-apps').click();
  await page.locator('#appearance').click();await page.locator('[data-theme-choice=light]').click();await page.keyboard.press('Escape');
  assert.equal(await page.locator('html').getAttribute('data-theme'),'light');await screen('menu-light-1024x600');
  await page.reload();assert.equal(await page.locator('html').getAttribute('data-theme'),'light');
  await page.locator('#launcher .end-sharing').click();await page.locator('#confirm-stop').click();assert(await page.locator('#ended').isVisible());
  await page.locator('#restart').click();assert(await page.locator('#launcher').isVisible());
  await page.locator('#appearance').click();await page.locator('[data-theme-choice=system]').click();await page.keyboard.press('Escape');
  await page.emulateMedia({colorScheme:'dark'});await page.waitForFunction(()=>document.documentElement.dataset.theme==='dark');
  await page.emulateMedia({colorScheme:'light'});await page.waitForFunction(()=>document.documentElement.dataset.theme==='light');
  rows.push({check:'empty recents, pagination, long names, arrow navigation, opening, return focus, fit/fill aspect, dialogs, stop/restart, persisted theme, system theme',passed:true});
  assert.deepEqual(errors,[]);
  const report={scope:'Browser design prototype only. No Android Auto, root, projection or performance validation.',checks:rows,browserErrors:errors};
  fs.writeFileSync(path.join(output,'checks.json'),JSON.stringify(report,null,2)+'\n');
  console.log(JSON.stringify(report,null,2));
 }finally{await browser.close();server.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
